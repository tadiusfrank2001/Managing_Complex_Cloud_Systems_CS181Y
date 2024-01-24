#!/bin/sh
#
# Script to dump a particular person's photoprism data in a few useful
# formats.
#
# Written 8 Oct 2004 by M. Dickerson

SQL="SELECT imageid, date, time, imagefile, width, height, size, title, caption, watermark.description, restriction.description, aperture, shutter, exposure, flash, film, process, focallength, metering, camera, location.description FROM image LEFT JOIN imageformat ON format=formatid LEFT JOIN restriction ON restriction=restrictid LEFT JOIN location ON location=locationid LEFT JOIN watermark ON watermark=watermark.watermarkid WHERE author=$1 ORDER BY imageid;"

PSQLFLAGS="-h nightfall"

echo "Dumping data:"
echo $SQL

# create html table
echo $SQL | psql $PSQLFLAGS -H photo photoprism >photoprism-$1.html
echo $SQL | psql $PSQLFLAGS -A -F , photo photoprism >photoprism-$1.csv
echo $SQL | psql $PSQLFLAGS -P format=latex photo photoprism >photoprism-$1.tex
