#!/usr/bin/python

import sys

for f in sys.stdin:
    if f.endswith('\n'): f = f[:-1]
    basename = f.split('/')[-1]
    if not basename.endswith('.jpg'):
        print '# bogus file: %s' % f
        continue
    try:
        id = int(basename.split('.')[0])
    except ValueError:
        print '# bogus file: %s' % f
        continue

    newpath = '/srv/photo/pkeep_orig/%s/%s/%s.jpg' % \
              (id % 10, (id / 10) % 10, id)
    print 'cp %s %s' % (f, newpath)
    
