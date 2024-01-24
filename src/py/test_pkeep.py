#!/usr/bin/python

import getopt
import socket
import sys

import pkeep
import s3auth

opts, remainder = getopt.getopt(sys.argv[1:], 'usc')
opts = dict(opts)

if '-u' in opts:
    # Send a udp request, wait for response.

    imgid, size = remainder

    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(5)
    s.connect(('localhost', pkeep.PORT))
    my_host, my_port = s.getsockname()
    req = [ my_host ]
    req.extend(map(str, [ my_port, 47, imgid, size, 0 ]))
    s.sendall(','.join(req))
    print 'Waiting for reply...'

    try:
      msg = s.recv(1024)
      rhost, rport = s.getpeername()
      print 'from %s: %s' % (rhost, msg)
    except Exception, e:
      print e

elif '-s' in opts:

    # Try to POST argv[3] to imageid argv[2].
    id, img = remainder
    blob = open(img, 'r').read()
    key = pkeep.RequestSpec('',0,0,id,0).orig_key()

    conn = s3auth.GetConn()
    print '--> %s' % key
    headers = { 'Content-Type': 'image/jpeg' }
    res = conn.put(pkeep.BUCKET, key, blob, headers=headers).http_response
    print '<-- %s %s' % (res.status, res.reason)
    
    key = pkeep.RequestSpec('',0,0,id,0).meta_key()
    md = 'TEXT_RIGHT (C) 2007 Michael Dickerson\n'
    headers['Content-Type'] = 'text/plain'
    print '--> %s' % key
    res = conn.put(pkeep.BUCKET, key, md, headers=headers).http_response
    print '<-- %s %s' % (res.status, res.reason)

elif '-c' in opts:

    # Clear cache for given image.
    id = remainder[0]

    conn = s3auth.GetConn()
    l = conn.list_bucket(pkeep.BUCKET, options={'prefix': 'cache/%s' % id})
    for key in [ x.key for x in l.entries ]:
        print '--> DELETE %s' % key
        res = conn.delete(pkeep.BUCKET, key).http_response
        print '<-- %s %s' % (res.status, res.reason)
         
    
