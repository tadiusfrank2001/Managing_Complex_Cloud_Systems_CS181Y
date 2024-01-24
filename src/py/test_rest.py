#!/usr/bin/python

import io
from json.decoder import JSONDecodeError
import requests
from PIL import Image, ImageDraw
import time

class TestError(Exception): pass

GET, POST = 0, 1
REST_TEST_CODE = '13723361'
REST_TEST_TAG = 'rest_test'

class RestTester:

   def __init__(self, server):
      self.errors = []
      self.server = server
      self.cookies = {}
      self.tests_run = []
      self.state_vars = {}

   def run_test(self, path, cookies=None, data=None, method=GET, files=None,
                status_code=None, content_type=None):
      self.tests_run.append(path)
      c = {GET: requests.get, POST: requests.post}[method]
      params = None
      if method == GET and data != None:
         params = data
         data = None
      if cookies is None: cookies = self.cookies
      r = c(self.server + path,
            cookies=cookies,
            data=data,
            params=params,
            files=files)
      if (status_code != None and r.status_code != status_code):
         self.errors.append("%s %s returned %d (expected %d)" %
                            ({GET: 'GET', POST: 'POST'}[method],
                             path, r.status_code, status_code))
         _dump_request(r)
      if (content_type != None):
         if 'content-type' in r.headers:
            ctype = r.headers['content-type'].split(';')[0]
         else:
            ctype = '';
         if content_type != ctype:
            self.errors.append("GET %s content-type %s (expected (%s)" %
                               (path, r.headers['content-type'], content_type))
      return r

   def add_error(self, msg):
      self.errors.append(str(msg))

   def __str__(self):
      msg = []
      msg.append("Ran %d tests" % len(self.tests_run))
      msg.append("Found %d problems" % len(self.errors))
      msg.extend(self.errors)
      return '\n'.join(msg)


def _dump_request(r):
   print('<== request')
   print(r.request.url)
   print(r.request.headers)
   print(r.request.body)
   print('==> response')
   print(r.headers)
   print(r.text)


def test_no_access(tester):
   tester.run_test('/img/7440', cookies={}, status_code=403)
   tester.run_test('/img/1000', cookies={}, status_code=403)
   tester.run_test('/img/47', cookies={}, status_code=403)
   tester.run_test('/img/100000001', cookies={}, status_code=403)
   tester.run_test('/rest/edit?img=47', cookies={}, status_code=403)
   tester.run_test('/rest/edit?img=1000000001', cookies={}, status_code=403)
   tester.run_test('/servlet/browserest', cookies={}, status_code=403)
   tester.run_test('/servlet/browserest?mode=r', cookies={}, status_code=403)
   tester.run_test('/servlet/searchrest', cookies={}, status_code=403)
   tester.run_test('/servlet/searchrest?l=1', cookies={}, status_code=403)


def test_token_api(tester):
   path = '/servlet/token'
   # the code validation endpoint should not accept any of these
   tester.run_test(path, method=POST, data={'stuff': 'nonsense'},
                   status_code=400)
   tester.run_test(path, method=POST, data={'rc': 'notacode' * 99},
                   status_code=403)
   tester.run_test(path, method=POST, data={'rc': 12345678},
                   status_code=403)

   # this will test validation of a correct code
   get_token(tester)
   correct_tagid = tester.state_vars['tagid']

   # even with a real token, these attempts to create and delete
   # nonsense codes should fail
   tester.run_test(path, method=POST,
                   data={'new': 1, 'tag': 9999999, 'lvl': 3},
                   status_code=403)
   tester.run_test(path, method=POST,
                   data={'new': 1, 'tag': correct_tagid, 'lvl': 4},
                   status_code=400)
   tester.run_test(path, method=POST,
                   data={'del': '000000000'}, status_code=403)
   tester.run_test(path, method=POST,
                   data={'del': '-1'}, status_code=403)

   # now make a legitimate attempt to create a new code, and look up
   # the results
   tester.run_test(path, method=POST,
                   data={'new': 1,
                         'tag': correct_tagid,
                         'lvl': 3,
                         'exp': (time.time() * 1000) + 30000,
                         'cnt': 2},
                   status_code=200)
   r = tester.run_test('/rest/tag?v=1', status_code=200)
   try:
      for tag in r.json():
         if tag['id'] == correct_tagid:
            for tok in tag['tok']:
               if tok['rc'] != REST_TEST_CODE:
                  new_code = tok['rc']
   except (KeyError, JSONDecodeError):
      tester.add_error('failed to retrieve new code')
      return

   # try validating the new code
   tester.run_test(path, method=POST, data={'rc': new_code}, status_code=200)

   # delete the code we just created
   tester.run_test(path, method=POST, data={'del': new_code}, status_code=200)

   # the code should now fail to validate
   tester.run_test(path, method=POST, data={'rc': new_code}, status_code=403)


def test_tag_api(t):
   if 'token' not in t.cookies:
      t.add_error('tried to run test_tag_api without token')
      return

   r = t.run_test('/rest/tag', cookies={}, status_code=200,
                  content_type='text/json')
   if (len(r.json()) != 0):
      t.add_error('non-empty tag GET with no cookies: %s' % r.json())

   r = tester.run_test('/rest/tag', status_code=200, content_type='text/json')
   tagid = None
   try:
      tagid = r.json()[0]['id']
   except (KeyError, JSONDecodeError):
      tester.add_error('bad tag response: ' + r.text)

   r = tester.run_test('/rest/tag', data={'v': 'true'},
                       status_code=200, content_type='text/json')
   try:
      j = r.json()[0]
      if j['tag'] != REST_TEST_TAG:
         tester.add_error('got tag name %s (expected %s)' %
                          (j['tag'], REST_TEST_TAG))
      if len(j['tok']) < 1:
         tester.add_error('token array was empty')
      elif j['tok'][0]['lvl'] != 4:
         tester.add_error('token had lvl set to %s (expected 4)' %
                          j['tok'][0]['lvl'])
      if 'id' not in j:
         tester.add_error('no tag id received')
   except (KeyError, JSONDecodeError):
      tester.add_error('bad tag GET response: ' + r.text)


def get_token(tester):   
   # get a token cookie and save it in the tester object
   r = tester.run_test('/servlet/token', method=POST,
                       data={'rc': REST_TEST_CODE}, status_code=200)
   if 'token' not in r.cookies:
      tester.add_error('got no token cookie')
   else:
      tester.cookies['token'] = r.cookies['token']

   # also look up and save the tagid for which we now have write
   # access, since other tests will want it
   r = tester.run_test('/rest/tag', status_code=200, content_type='text/json')
   try:
      for tag in r.json():
         if tag['tag'] == REST_TEST_TAG:
            tester.state_vars['tagid'] = tag['id']
   except (KeyEror, JSONDecodeError):
      tester.add_error('bad tag GET response: ' + r.text)


def test_create_image(tester):
   path = '/rest/upload'
   img = Image.new('RGB', (600,400))
   ImageDraw.Draw(img).text((100,200), "i'm a bangin test image")
   imgbytes = io.BytesIO()
   img.save(imgbytes, format='jpeg')
   del img

   fileparts = {'file': ('testimage.jpeg', imgbytes, 'image/jpeg')}
   imgbytes.seek(0)

   # if you try to post an image with no tags, it should fail.
   tester.run_test(path, method=POST, files=fileparts, status_code=400)

   if not 'tagid' in tester.state_vars:
      raise TestError('have to run test_token_api first')

   # now try to post an image with a tag and some metadata
   imgbytes.seek(0)
   data = {'tag': tester.state_vars['tagid'],
           'cam': 'not really a camera',
           'wmk': '(C) (year) your mom',
           'tz': 'US/Pacific'}
   r = tester.run_test(path, method=POST, files=fileparts, data=data,
                       status_code=200, content_type='text/json')

   ids = None
   try:
      ids = r.json()['ids']
   except (KeyError, JSONDecodeError) as e:
      tester.add_error('upload response was not json: ' + r.text)

   if ids != None:
      if len(ids) != 1:
         tester.add_errors('got %d ids in image response (expected 1)' %
                           len(ids))
      else:
         tester.state_vars['imageid'] = ids[0]

   if 'imageid' in tester.state_vars:
      # see if we can get the new image
      imgpath = '/img/%d' % tester.state_vars['imageid']
      tester.run_test(imgpath, status_code=200, content_type='image/jpeg')
      # see if it fails without the correct token cookie
      tester.run_test(imgpath, cookies={'token': 'xyz'}, status_code=403)


def test_edit_image(t):
   if not 'imageid' in t.state_vars:
      t.add_error('tried to run test_edit_image with no image')
      return

   data = {'img': t.state_vars['imageid']}
   data['cap'] = 'yappity yap caption'
   t.run_test('/rest/edit', method=POST, data=data, status_code=200)

   del data['cap']
   data['flm'] = 'fujifake'
   data['loc'] = 11
   t.run_test('/rest/edit', method=POST, data=data, status_code=200)

   del data['flm']
   del data['loc']
   r = t.run_test('/rest/edit', data=data,
                  status_code=200, content_type='text/json')

   try:
      j = r.json()
      if j['cap'] != 'yappity yap caption':
         t.add_error('caption came back wrong: ' + j['cap'])
      if j['flm'] != 'fujifake':
         t.add_error('film came back wrong: ' + j['flm'])
      if j['loc'][0]['id'] != 11:
         t.add_error('location came back wrong: ' + str(j['loc']))
      if j['wmk'] != '(C) (year) your mom':
         t.add_error('watermark came back wrong: ' + str(j['wmk']))
   except (KeyError, JSONDecodeError):
      t.add_error('bad edit response: ' + r.text)
      

def test_delete_image(t):
   if not 'imageid' in t.state_vars:
      t.add_error('tried to run test_delete_image with no image')
      return
   data = {'img': t.state_vars['imageid']}
   r = t.run_test('/rest/edit', data=data, status_code=200)
   if r.status_code == 200 and 'del' in r.json():
      t.add_error('image was deleted before we asked')
   data['del'] = 'true'
   t.run_test('/rest/edit', method=POST, data=data, status_code=200)
   r = t.run_test('/rest/edit', data=data, status_code=200)
   if r.status_code == 200 and 'del' not in r.json():
      t.add_error('image did not get deleted')
   del t.state_vars['imageid']


def test_suggests(t):
   path = '/rest/suggest'
   try:
      j = t.run_test(path, status_code=200).json()
      if (len(j) != 0):
         t.add_error('expected empty response with no params')
      j = t.run_test(path + '?fjfj=1&sksk=2', status_code=200).json()
      if (len(j) != 0):
         t.add_error('expected empty response with nonsense params')
      j = t.run_test(path + '?tmz=1', status_code=200).json()
      if (not 'US/Pacific' in j['tmz']['opt']):
         t.add_error('expected US/Pacific in the timezone suggestions')
      if (not j['tmz']['dfl'] in j['tmz']['opt']):
         t.add_error('timezone default %s missing from opt' % j['tmz']['dfl'])
      j = t.run_test(path + '?loc=1', status_code=200).json()
      if (not j['loc'][0]['txt'] == 'Earth'):
         t.add_error('did not find Earth in location suggestion')
      j = t.run_test(path + '?loc=11', status_code=200).json()
      if (j['loc'][0]['txt'] == 'Earth'):
         t.add_error('did not expect to find Earth in location 11')
      j = t.run_test(path + '?tmz=1&wmk=1', status_code=200).json()
      if (not ('tmz' in j and 'wmk' in j)):
         t.add_error('did not get both tmz and wmk in combined request')
   except (KeyError, JSONDecodeError) as e:
      t.add_error('unparseable suggest response: %s' % e)


if __name__ == '__main__':

    t = RestTester('http://skywise.local:8080')
    test_no_access(t)
    test_token_api(t)
    test_create_image(t)
    test_edit_image(t)
    test_no_access(t)
    test_suggests(t)
    test_delete_image(t)
    print(t)
    
