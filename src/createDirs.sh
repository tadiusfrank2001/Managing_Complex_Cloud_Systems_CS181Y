#!/bin/sh
# Script to create the directory structure that needs to exist before
# a particular person can log in and upload pictures.  Takes one argument
# which is the PersonID from the database.
#
# Written 10 Mar 2004 by M. Dickerson.

if [ -z "$1" ]; then
  echo "Usage: $0 id"
  echo "where id is a PersonID from the database."
  exit 1
fi

cd /usr/local/var/photo
mkdir $1
cd $1
mkdir 1 2 3 4 5 6 7 8 9 10 11 12 upload
cd ..
chown -Rh www $1
