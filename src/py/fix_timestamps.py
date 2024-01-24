#!/usr/bin/python
#
# reread the EXIF timestamps from the original files and combine with
# the timezones which are only recorded in the database.  Turns out this
# would probably have created more problems than it solved, so it was
# never applied.
#
# 2019-04-20 mikey

import os
import piexif
import psycopg2

def imagefile(id):
  if len(str(id)) < 2:
    last_digit = str(id)
    second_last_digit = '0'
  else:
    last_digit = str(id)[-1]
    second_last_digit = str(id)[-2]
  return '/srv/photo/pkeep_orig/%s/%s/%s.jpg' % (last_digit,
                                                 second_last_digit,
                                                 str(id))

if __name__ == '__main__':
  db = psycopg2.connect(database='photo', user='photoprism')
  cur = db.cursor()
  cur.execute('SELECT imageid, timezone, ts AT TIME ZONE timezone '
              'FROM image ORDER BY 1;')
  rs = cur.fetchall()
  cur.close()
  for row in rs:
    imgid = row[0]
    imgfile = imagefile(imgid)
    timezone = row[1]
    ts = str(row[2])
    try:
      os.stat(imgfile)
    except FileNotFoundError:
      print('--E %d file not found: %s' % (imgid, imgfile))
      continue
    try:
      exif = piexif.load(imgfile)
    except Exception as e:
      print('--E %d piexif failed: %s' % (imgid, e))
      continue
    datetime = exif.get('Exif').get(piexif.ExifIFD.DateTimeOriginal) \
      or exif.get('0th').get(piexif.ImageIFD.DateTime) \
      or exif.get('Exif').get(piexif.ExifIFD.DateTimeDigitized)
    if not datetime:
      print('-- E %d no exif timestamp' % imgid)
      continue
    datetime = datetime.decode('ascii').replace(':', '-', 2)    
    if datetime != ts:
      print('-- %d exif=%s timezone=%s ts=%s' %
            (imgid, datetime, timezone, ts))
      print("UPDATE image SET ts = TIMESTAMP '%s' AT TIME ZONE '%s' "
            "WHERE imageid=%s;" % (datetime, timezone, imgid))
    
