#!/bin/sh
#
# This script fixes common permissions problems that happen when
# setting up a new site.
#
# Prerequisites:
# + sudo must work
# + pkeep_local.py should be running
# + tomcat should be running
# + postgres should be running
# + pg_hba.conf must be set up in a way that this PSQL command works and
#   gives you superuser permissions:
PSQL='psql -t photo postgres'

if ! echo 'SELECT 1;' | ${PSQL} >/dev/null ; then
    echo "This must work and give superuser permissions: ${PSQL}"
    exit 1
fi

# Permissions fix idea from:
# https://stackoverflow.com/questions/1348126/postgresql-modify-owner-on-all-tables-simultaneously-in-postgresql/2686185#2686185

NEW_OWNER=photoprism
TMPFILE=`mktemp /tmp/${0}.XXXXX`

${PSQL} >${TMPFILE} <<EOF
SELECT 'ALTER TABLE ' || schemaname || '."' || tablename ||
       '" OWNER TO ${NEW_OWNER};'
FROM pg_tables
WHERE NOT schemaname IN ('pg_catalog', 'information_schema')
ORDER BY 1;
SELECT 'ALTER SEQUENCE '|| sequence_schema || '."' || sequence_name ||
       '" OWNER TO ${NEW_OWNER};'
FROM information_schema.sequences
WHERE NOT sequence_schema IN ('pg_catalog', 'information_schema')
ORDER BY 1;
SELECT 'ALTER VIEW ' || table_schema || '."' || table_name ||
       '" OWNER TO my_new_owner;'
FROM information_schema.views
WHERE NOT table_schema IN ('pg_catalog', 'information_schema')
ORDER BY 1;
EOF

cat >>${TMPFILE} <<EOF

SELECT setval('imagesubject_psid_seq', max(psid)) FROM imagesubject;
SELECT setval('imagetag_id_seq', max(id)) FROM imagetag;
SELECT setval('imageviews_viewid_seq', max(viewid)) FROM imageviews;
SELECT setval('location_locationid_seq', max(locationid)) FROM location;
SELECT setval('person_personid_seq', max(personid)) FROM person;
SELECT setval('seq_imageid', max(imageid)) FROM image;
SELECT setval('tag_id_seq', max(id)) FROM tag;
SELECT setval('token_id_seq', max(id)) FROM token;

DROP TABLE IF EXISTS gpslog;
DROP SEQUENCE IF EXISTS gpslog_gpslogid_seq;
DROP TABLE IF EXISTS restriction;
DROP SEQUENCE IF EXISTS restriction_restrictid_seq;
DROP TABLE IF EXISTS userrestrict;
DROP SEQUENCE IF EXISTS userrestrict_urid_seq;

ALTER USER photoprism WITH NOSUPERUSER;
EOF

cat ${TMPFILE}
echo
echo 'Should I run the above? Enter to proceed, Ctrl-C to cancel.'
read TRASH
${PSQL} < ${TMPFILE}
echo
echo 'Done.'

PKEEP_PS=`ps auxw | grep pkeep_local.py | grep -v grep`
PKEEP_OWNER=`echo ${PKEEP_PS} | cut -d' ' -f1`
if [ -z "${PKEEP_OWNER}" ]; then
    echo "Can't determine pkeep process owner.  Is it running?"
    exit 1
fi
PKEEP_ORIG=`echo ${PKEEP_PS} | fmt -w 1 | grep pkeep_orig`
PKEEP_CACHE=`echo ${PKEEP_PS} | fmt -w 1 | grep pkeep_cache`

TOMCAT_OWNER=`ps auxw | grep tomcat | grep -v grep | cut -d' ' -f1`
if [ -z "${TOMCAT_OWNER}" ]; then
    echo "Can't determine tomcat process owner.  Is it running?"
    exit 1
fi

cat <<EOF
Looks like your process owners are:
  pkeep is run by ${PKEEP_OWNER}
  tomcat is run by ${TOMCAT_OWNER}

Looks like your jpeg storage directories are:
  pkeep_orig  is at: ${PKEEP_ORIG}
  pkeep_cache is at: ${PKEEP_CACHE}

Does this look right?  Enter to proceed, Ctrl-C to cancel.
EOF
read TRASH

echo "Setting all of ${PKEEP_CACHE} to be owned by ${PKEEP_OWNER}"
sudo chown -R ${PKEEP_OWNER}:${PKEEP_OWNER} ${PKEEP_CACHE}
echo "Setting all of ${PKEEP_ORIG} to be owned by ${TOMCAT_OWNER}"
sudo chown -R ${TOMCAT_OWNER}:${TOMCAT_OWNER} ${PKEEP_ORIG}
echo "Setting all directory permissions to 755 and all files to 644"
sudo find ${PKEEP_ORIG} -type d -exec chmod 755 {} \;
sudo find ${PKEEP_CACHE} -type d -exec chmod 755 {} \;
sudo find ${PKEEP_ORIG} -type f -exec chmod 644 {} \;
sudo find ${PKEEP_CACHE} -type f -exec chmod 644 {} \;
echo "Adding ${PKEEP_OWNER} to the ${TOMCAT_OWNER} group"
sudo usermod -a -G ${TOMCAT_OWNER} ${PKEEP_OWNER}

echo
echo "Success! Some things might work better now."
echo "SEE YOU SPACE COWBOY"
