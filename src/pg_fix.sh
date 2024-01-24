#!/bin/sh
#
# This script fixes common permissions problems that happen when
# setting up a new site.
#
# Postgres authentication (pg_hba.conf) must be set up in a way that this PSQL
# command works and gives you superuser permissions:

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

PKEEP_OWNER=`ps auxw | grep pkeep_local.py | grep -v grep | cut -d' ' -f1`
if [ -z "${PKEEP_OWNER}" ]; then
    echo "Can't determine pkeep process owner.  Is it running?"
    exit 1
fi

TOMCAT_OWNER=`ps auxw | grep tomcat | grep -v grep | cut -d' ' -f1`
if [ -z "${TOMCAT_OWNER}" ]; then
    echo "Can't determine tomcat process owner.  Is it running?"
    exit 1
fi

echo "Looks like your process owners are:"
echo "  pkeep is run by ${PKEEP_OWNER}"
echo "  tomcat is run by ${TOMCAT_OWNER}"
echo
echo "Does this look right?  Enter to proceed, Ctrl-C to cancel."
read TRASH

echo "Setting all of /srv/photo/pkeep_cache to be owned by ${PKEEP_OWNER}"
sudo chown -R ${PKEEP_OWNER}:${PKEEP_OWNER} /srv/photo/pkeep_cache
echo "Setting all of /srv/photo/pkeep_orig to be owned by ${TOMCAT_OWNER}"
sudo chown -R ${TOMCAT_OWNER}:${TOMCAT_OWNER} /srv/photo/pkeep_orig
echo "Setting all directories in /srv/photo to 755"
sudo find /srv/photo -type d -exec chmod 755 {} \;
echo "Setting all files in /srv/photo to 644"
sudo find /srv/photo -type f -exec chmod 644 {} \;
echo "Adding ${PKEEP_OWNER} to the ${TOMCAT_OWNER} group"
sudo usermod -a -G ${TOMCAT_OWNER} ${PKEEP_OWNER}
