#!/usr/bin/python
#
# pkeep.py - The photo keeper.
#
# This version does not touch Amazon S3.  Instead we assume the canonical image
# store is a local directory.  To run, we must have the path to that local
# image store, and another path where we can write ephemeral cache files.
#
# The sample image set, in the correct directory structure, is provided in
# sample_images.tar.xz in the src directory.  This should go in your pkeep_orig
# directory.  pkeep_cache must exist with correct permissions, but can be empty.
#
# 2020-07-28 mikey: this is what you are looking for:
# python2 pkeep_local.py -w 2 -c /srv/photo/pkeep_cache/ -o /srv/photo/pkeep_orig/

import BaseHTTPServer
import Queue
import getopt
import md5
import os
import socket
import sys
import threading
import time

import imgops

FILE_CACHE_EXP = 3600

PORT = 4770
WORKERS = 2
REQUEST_EXPIRES = 60 # seconds

# salt isn't important for any of the usual crypographic reasons, but
# you can permute all of the cache file names by changing its value,
# such as to force images to be regenerated after a change to imgops.py.

SALT = '1.0'

VARS = {
  'requests': 0,
  'requests_expired': 0,
  'fecache_hits': 0,
  'origfile_cache_hits': 0,
  'origfile_cache_hit_rate': 0.0,
  'frontend_cache_fetches': 0,
  'missing_files': 0,
  'worker_time_mean': 0.0,
  'worker_time_total': 0.0,
  'latency_mean': 0.0,
  'latency_total': 0.0,
  'version': 1.2,
  'render_instructions': '',
} # Yes, it's spelled 'vars'.  Why do you ask?

req_queue = Queue.Queue()
ans_queue = Queue.Queue()
log_buf = []
xorig_image_root = None

INFO, WARN, ERR = 0, 1, 2

def log(msg, level=INFO):
  global log_buf
  lvl = ('I', 'W', 'E')[level]
  now = time.strftime('%y%m%d %H:%M.%S')
  msg = ' '.join([lvl, now, msg])
  print msg
  log_buf.append(msg)
  log_buf = log_buf[-100:]


class RequestSpec:

  def __init__(self, *args):
    self.addr  = args[0]      # Where to send response
    self.req   = int(args[1]) # Request serial number, ticket number, whatever
    self.id    = int(args[2]) # ImageId
    self.size  = int(args[3]) # Longer edge, in pixels
    self.inst  = args[4]      # Rendering instructions (str)
    self.time  = time.time()
    self.response = None

  def GetOrigFile(self):
    global orig_image_root
    return os.path.join(orig_image_root,
                        str(self.id % 10),
                        str((self.id / 10) % 10),
                        '%s.jpg' % self.id)

  def GetCacheFile(self, cache_dir):
    fname = '%06d.%d.%s.jpg' % (self.id,
                                self.size,
                                md5.md5(SALT + self.inst).hexdigest()[-6:])
    return os.path.join(cache_dir, '%02d' % (self.id % 100), fname)
    
  def __str__(self):
    return '%s:%s %s/%s' % (self.addr, self.req,
                            self.id, self.size)


def HandleUDP(text, addr):
  """When a packet comes in, parse it and queue it for a worker."""

  try:
    args = [addr]
    args.extend([x.strip() for x in text.split(',')])
    rs = RequestSpec(*args)
  except (ValueError, IndexError), e:
    log('ignoring bogus request from %s: %s' % (addr, text), level=ERR)
    log(str(e))
    return

  global req_queue
  req_queue.put(rs)
  log('queued %s, %d in queue' % (rs, req_queue.qsize()), level=INFO)


class VarsHandler(BaseHTTPServer.BaseHTTPRequestHandler):

  def do_GET(self):
    self.send_response(200, 'OK')
    self.send_header('Content-Type', 'text/plain')
    self.end_headers()
    if self.path == '/log':
      global log_buf
      self.wfile.write('\n'.join(log_buf))
    else:
      global VARS
      keys = VARS.keys()
      keys.sort()
      for k in keys: self.wfile.write('%s %s\n' % (k, VARS[k]))
      

class VarsServer(threading.Thread, BaseHTTPServer.HTTPServer):

  def __init__(self, addr, **kwargs):
    threading.Thread.__init__(self, **kwargs)
    BaseHTTPServer.HTTPServer.__init__(self, addr, VarsHandler)

  def run(self):
    self.serve_forever()
    

class ImageWorker(threading.Thread):

  def __init__(self, cache_dir=None, **kwargs):
    threading.Thread.__init__(self, **kwargs)
    self.cache_dir = cache_dir

  def run(self):
    global req_queue
    global ans_queue
    global VARS

    while True:
      req = req_queue.get()
      start_time = time.time()

      # Pressure release, so we don't stay backlogged forever if somebody loads
      # a big page and goes away
      req_age = int(start_time - req.time)
      if req_age > REQUEST_EXPIRES:
        log('punting on %ds old request: %s' % (req_age, req))
        VARS['requests_expired'] += 1
        req.response = 'expired'
        ans_queue.put(req)
        continue

      if req.size == -1:
        url = self._ClearCache(req)
      else:
        url = self._RenderImage(req)

      req.response = url
      ans_queue.put(req)
      
      VARS['requests'] += 1
      VARS['render_instructions'] = req.inst
      VARS['origfile_cache_hit_rate'] = float(VARS['origfile_cache_hits']) \
                                    / VARS['requests']
      VARS['worker_time_total'] += time.time() - start_time
      VARS['worker_time_mean'] = VARS['worker_time_total'] / \
                                 VARS['requests']


  def _ClearCache(self, req):
    """Try to delete all cached files relevant to the image in req.id.

    Returns the str 'ok' if successful, otherwise returns the str
    description of the OSError.
    """
    cachedir = os.path.join(self.cache_dir, str(req.id % 10))
    files = os.listdir(cachedir)
    files = filter(lambda x: x.startswith('%s.' % req.id), files)
    try:
      for f in [ os.path.join(cachedir, x) for x in files ]:
        log('deleting: %s' % f)
        os.unlink(os.path.join(cachedir, f))
    except OSError, e:
      log('failed to delete %s: %s' % (f, e))
      return str(e)

    return 'ok'        
  

  def _RenderImage(self, req):
    """Get a URL that satisfies req.

    Returns a string that starts with either file:// or http://, depending
    on whether we have a configured landing area for local cache files.
    """

    # If we already have this exact request locally, return it.
    cachefile = req.GetCacheFile(self.cache_dir)
    if os.path.exists(cachefile):
      VARS['fecache_hits'] += 1
      return 'file://%s' % cachefile

    origfile = req.GetOrigFile()
    if not os.path.exists(origfile):
      VARS['missing_files'] += 1
      return 'OH NOES: can\'t find original image file'

    if req.size == 0: return 'file://%s' % origfile

    # Render image.
    try:
      imgops.render(origfile, cachefile, req.size, req.inst)
    except imgops.Error, e:
      log('Failed to render %s: %s' % (cachefile, e))
      return None

    return 'file://%s' % cachefile


if __name__ == '__main__':
  opts, remainder = getopt.getopt(sys.argv[1:], 'p:w:c:o:')
  opts = dict(opts)

  opts.setdefault('-p', PORT)
  opts.setdefault('-w', WORKERS)
  opts.setdefault('-c', None)
  opts.setdefault('-o', None)

  port = int(opts['-p'])

  # Start http status thread
  t = VarsServer(('', port), name='VarsServer')
  t.setDaemon(True)
  t.start()

  # Prepare frontend cache tree if needed.
  if opts['-c']:
    log('maintaining frontend cache at %s' % opts['-c'])
    for i in range(100):
      d = os.path.join(opts['-c'], '%02d' % i)
      if not os.path.isdir(d):
        os.mkdir(d)
        log('created cache dir %s' % d)
  else:
    log('-c (path to serving cache) is required.')
    sys.exit(1)

  if opts['-o']:
    global orig_image_root
    orig_image_root = opts['-o']
  else:
    log('-o (path to image storage root) is required.')
    sys.exit(1)

  # Start the worker threads.
  threads = []
  while len(threads) < int(opts['-w']):
    log('starting worker thread')
    t = ImageWorker(cache_dir=opts['-c'], name='Worker-%d' % len(threads))
    t.setDaemon(True)
    t.start()
    threads.append(t)

  # Main thread sends and receives on the UDP socket.
  s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  try:
    s.setblocking(False)
    s.bind(('', port))
  except socket.error, e:
    log('can\'t bind to port %d: %s' % (port, e), level=ERR)
    sys.exit(1)
  log('listening on port %d/udp' % port)

  try:
    while True:
      try:
        msg, addr = s.recvfrom(1024)
        if msg.startswith('KTHXBYE'): break
        HandleUDP(msg, addr)
      except socket.error:
        pass

      try:
        req = ans_queue.get(True, 0.1)
        msg = '%s,%s' % (req.req, req.response)
        s.sendto(msg, req.addr)
        latency = time.time() - req.time
        log('served %s -> %s in %.3fs' % (req, req.response, latency))
        VARS['latency_total'] += time.time() - req.time
        VARS['latency_mean'] = VARS['latency_total'] / \
                               VARS['requests']
        del req
      except Queue.Empty:
        pass

  except KeyboardInterrupt:
    pass
  
  log('see you space cowboy')
