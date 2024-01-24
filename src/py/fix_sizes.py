#!/usr/bin/python

"""fix_sizes.py - because a lot of images have somehow ended up with
width == height == null.
"""

import getopt
import os
import sys

from pyPgSQL import PgSQL

import Image

if __name__ == '__main__':

  try:
    opts, remainder = getopt.getopt(sys.argv[1:], 'n')
    opts = dict(opts)
  except getopt.GetoptError, e:
    sys.stderr.write('%s\n%s\n' % (e, __doc__))
    sys.exit(1)

  db = PgSQL.connect(host='localhost', database='photo', user='photoprism')
  c = db.cursor()
  c.execute('SELECT imageid, author, date_part(\'year\', ts), '
            'date_part(\'month\', ts), imagefile FROM image '
            'WHERE width IS NULL OR height IS NULL;')
  rs = c.fetchall()
  for row in rs:
    id, author, year, month, imgfile = row
    if imgfile == 's3':
      # have to go looking for it.  Might not be in the month suggested by the
      # metadata, because it might have sat in the camera for a while before
      # being uploaded.
      imgfile = '/nonexistent'
      year, month = int(year), int(month)
      tries = 0
      while not os.path.exists(imgfile):
        imgfile = '/home/photo/%s/%s/%s/%s.jpg' % \
                  (author, year, month, id)
        month += 1
        if month > 12:
          month = 1
          year += 1
        tries += 1
        if tries > 120:
          sys.stderr.write('Can\'t find original file for %s\n' % id)
          break
        
    try:
      w, h = Image.open(imgfile).size
    except IOError, e:
      sys.stderr.write('%s\n' % e)
      continue
    sql = 'UPDATE image SET width=%s, height=%s WHERE imageid=%s;' % (w, h, id)
    if '-n' in opts:
      print sql
    else:
      c.execute(sql)

    db.commit()
