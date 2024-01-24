#!/bin/sh

YEAR=`date +%Y`
USERS=`cd /home/photo; ls -d [1-9]*`
for USER in $USERS ; do ./create-archive-cd.sh $USER $YEAR ; done
