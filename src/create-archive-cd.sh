#!/bin/sh
#
# Script to prepare archive CD for a particular person and year.  Year can be
# given as a shell-acceptable range (e.g. "200[0123]").
#
# Written 8 Oct 04 by M. Dickerson

ISO=/home/photo/photo-$1-$2.iso

echo "Attempting to create ISO image $ISO..."

./dump-photo-data.sh $1
mkisofs -J -r -o $ISO -V "PHOTOS-$2" /home/photo/$1/$2 photoprism-$1.*
rm photoprism-$1.*
