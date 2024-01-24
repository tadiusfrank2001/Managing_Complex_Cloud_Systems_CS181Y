#!/usr/bin/python

"""migrate.py - Migrate images from local disk storage into s3.

Usage: migrate.py [-t n] [-r n] [-l] id0 id1 id2 ...
-t : also migrate the n most often viewed images
-r : also migrate the n most recent images
-l : be lazy and don't prepopulate common cache sizes
-a : start auto-migration loop
"""

from boto.exception import S3ResponseError
import getopt
import math
import os
import random
import sys
import tempfile
import time

from pyPgSQL import PgSQL

import imgops
import pkeep
import s3auth

def ShowProgress(m, n):
  if sys.stdout.isatty():
    sys.stdout.write('\r%8d of %d bytes (%.3f)' % (m, n, float(m)/n))
    if m == n: sys.stdout.write('\n')
    sys.stdout.flush()


def MigrateImage(db, bucket, imgid, lazy=False):

  imgid=int(imgid)

  c = db.cursor()
  c.execute('SELECT p.firstname, p.lastname, p.username, '
            'i.restriction, i.imagefile, i.rotation, '
            'date_part(\'year\', i.ts), f.mimetype, w.description, '
            'i.caption '
            'FROM image i INNER JOIN person p ON i.author = p.personid '
            'INNER JOIN imageformat f ON i.format = f.formatid '
            'INNER JOIN watermark w ON i.watermark = w.watermarkid '
            'WHERE i.imageid = %s;' % imgid)
  rs = c.fetchall()
  if len(rs) == 0:
      print '%s: No row in database' % imgid
      return
  data = rs[0]

  FN, LN, LOGIN, REST, FILE, ROT, YEAR, MIME, WM, CAP = range(10)

  fake_req = pkeep.RequestSpec('', 0, 0, imgid, 0)

  if not os.path.exists(data[FILE]):
    print '%s: No such file %s' % (imgid, data[FILE])
    return
  
  if data[WM] not in ('Copyright', 'Username', 'Caption', 'None'):
    print '%s: Can\'t migrate watermark %s' % (imgid, data[WM])
    return

  key = fake_req.orig_key()
  headers = { 'Content-Type': data[MIME] }

  print '--> %s' % key
  k = bucket.new_key(key)
  k.set_contents_from_filename(data[FILE], headers=headers, cb=ShowProgress,
                               num_cb=50)
  # print '<-- %s %s' % (res.status, res.reason)

  metadata = ''
  if data[REST] == 1:
    metadata += 'PUBLIC\n'

  if data[ROT] != 0:
    metadata += 'ROTATE %s\n' % data[ROT]

  if data[WM] == 'Copyright':
    metadata += 'TEXT_RIGHT (C) %s %s %s\n' % \
                (int(data[YEAR]), data[FN], data[LN])
  elif data[WM] == 'Username':
    metadata += 'TEXT_RIGHT (C) %s %s\n' % \
                (int(data[YEAR]), data[LOGIN])
  elif data[WM] == 'Caption':
    metadata += 'TEXT_LEFT %s\n' % data[CAP]

  key = fake_req.meta_key()
  headers['Content-Type'] = 'text/plain'
  print '--> %s' % key
  k = bucket.new_key(key)
  k.set_contents_from_string(metadata, headers=headers)

  if not lazy:
    for s in (140, 250, 350, 640, 1024):
      fake_req.size = s
      cachefile = pkeep.GetLocalCacheFile('/srv/pkeep_cache', fake_req)
      print '--> %s' % cachefile
      imgops.render(data[FILE], cachefile, s, metadata)

  sql = 'UPDATE image SET imagefile=\'s3\' WHERE imageid=%s;' % imgid
  print sql
  c.execute(sql)
  db.commit()
  return


def FindTopN(db, n):
  """Find the N images with the most pageviews that are not already in S3."""
  c = db.cursor()
  c.execute('SELECT i.imageid, count(v.viewid) FROM imageviews v '
            'INNER JOIN image i ON i.imageid = v.image '
            'WHERE i.imagefile != \'s3\' GROUP BY i.imageid '
            'ORDER BY 2 DESC LIMIT %s;' % n)
  ids = [ x[0] for x in c.fetchall() ]
  c.close()
  return ids


def FindRecentN(db, n):
  """Find the N newest images that are not already in s3."""
  c = db.cursor()
  c.execute('SELECT imageid FROM image '
            'WHERE imagefile != \'s3\' '
            'ORDER BY 1 DESC LIMIT %s;' % n)
  ids = [ x[0] for x in c.fetchall() ]
  c.close()
  return ids


def FindChangedN(db, n):
  """Find N changed images that are waiting to be synced to s3."""
  c = db.cursor()
  c.execute('SELECT foreignkey FROM change '
            'WHERE description = \'image metadata changed\' '
            'AND synced = false ORDER BY changetime LIMIT %s;' % n)
  ids = [ x[0] for x in c.fetchall() ]
  c.close()
  return ids
  


def AutoMigrate(db, bucket):
  """Super fancy."""

  def _delay():
    # Fraction of current day elapsed in local time (0=0:00, 0.25=6:00, ..)
    day_off = (int(time.time() - time.timezone) % 86400) / 86400.0
    # Back up by 10h so that peak is at 16:00 and trough at 4:00
    day_off -= 10/24.0
    # Modulate in sine wave, from min of 10s to max of 110s
    return 60 + math.sin(2*math.pi*day_off) * 50

  todo = []

  while True:
    try:
      if not todo:
        # todo = FindChangedN(db, 10)
        if not todo:
          todo.extend(FindTopN(db, 10))
          todo.extend(FindRecentN(db, 10))
          random.shuffle(todo)
      if todo:
        img = todo.pop()
        try:
          MigrateImage(db, bucket, img)
        except S3ResponseError, e:
          print 's3 error: %s' % e
      d = _delay()
      print 'sleeping %.3fs' % d
      time.sleep(d)
    except KeyboardInterrupt:
      break

  print 'see you space cowboy'

  
if __name__ == '__main__':

  try:
    opts, remainder = getopt.getopt(sys.argv[1:], 't:r:la')
    opts = dict(opts)
  except getopt.GetoptError, e:
    sys.stderr.write('%s\n%s\n' % (e, __doc__))
    sys.exit(1)

  db = PgSQL.connect(host='localhost', database='photo', user='photoprism')
  s3 = s3auth.connect_s3()
  bucket = s3.get_bucket(pkeep.BUCKET)
  if '-t' in opts: remainder.extend(FindTopN(db, opts['-t']))
  if '-r' in opts: remainder.extend(FindRecentN(db, opts['-r']))
  for imgid in remainder: MigrateImage(db, bucket, imgid, lazy=('-l' in opts))
  if '-a' in opts: AutoMigrate(db, bucket)
  db.close()
  
