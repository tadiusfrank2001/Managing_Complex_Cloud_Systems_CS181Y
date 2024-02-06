#!/usr/bin/python
#
# If you get 'cannot import Image', you don't have PIL.  On Debian,
# apt-get install python-imaging.

from PIL import Image, ImageDraw, ImageFile, ImageFilter, ImageFont

# 2019-04-07 mikey: make pillow tolerate weird jpeg block sizes.  This
# affects some hundreds of images in my set of 30,000.

ImageFile.LOAD_TRUNCATED_IMAGES = True


class Error(Exception): pass


def _Font():
  return ImageFont.truetype('Dustismo.ttf', 16, encoding='unic')


def _AvgBrightness(img):
  """Return mean brightness of the pixels in an image.

  'Brightness' is approximated on RGB images; it's really just the
  green value.
  """
  if len(img.getbands()) > 1:
    hist = img.split()[1].histogram()
  else:
    hist = img.histogram()
  total = sum([i * hist[i] for i in xrange(len(hist))])
  return total / sum(hist) # integer division is ok


def render(oldfile, newfile, size, instructions):

  im = Image.open(oldfile)

  origw, origh = im.size
  if origw > origh:
    neww, newh = size, int(float(origh) / origw * size)
  else:
    neww, newh = int(float(origw) / origh * size), size

  try:
    im = im.resize( (neww, newh), Image.ANTIALIAS )
  except IOError as e:
    raise Error('IOError from PIL: %s' % e)

  for step in instructions.split('\n'):
    if step.find(' ') < 0: continue
    cmd, arg = step.split(' ', 1)
    cmd = cmd.upper()

    if cmd.startswith('TEXT_'):

      font = _Font()
      textw, texth = font.getsize(arg)
      texty = im.size[1] - texth - 20
      if cmd.endswith('RIGHT'):
        textx = im.size[0] - textw - 20
        # RIGHT text is probably a "(C) 2008 whatever", looks bad if it extends
        # across the whole bottom of the image
        if neww < textw or texty < 0: continue
      elif cmd.endswith('LEFT'):
        textx = 20
        # LEFT text is probably a caption, it can be long
        if neww < textw + textx or texty < 0: continue
      else:
        raise Error('Bogus metadata command: %s' % cmd)

      crop = im.crop((textx, texty, textx+textw, texty+texth))
      if _AvgBrightness(crop) < 100:
        # 128 is the midpoint, but bias slightly toward black since it
        # tends to look better.
        color = 'white'
      else:
        color = 'black'
        
      # This is a lot easier than trying to get the encoding right
      # through Java -> S3 -> Python.
      if arg.find('(C)') >= 0 or arg.find('(c)') >= 0:
        arg = unicode(arg)
        arg = arg.replace('(C)', u'\u00a9')
        arg = arg.replace('(c)', u'\u00a9')

      ImageDraw.Draw(im).text((textx, texty), arg, font=font, fill=color)

    elif cmd == 'ROTATE':

      try:
        rot = { '90': Image.ROTATE_90,
                '180': Image.ROTATE_180,
                '270': Image.ROTATE_270,
                'h': Image.FLIP_LEFT_RIGHT,
                'v': Image.FLIP_TOP_BOTTOM }[arg]
      except KeyError:
        continue

      im = im.transpose(rot)

    else:

      raise Error('Bogus metadata command: %s' % cmd)
    

  # im = im.filter(ImageFilter.SHARPEN)
  # im.save(newfile, optimize=True, quality=85)
  im.save(newfile)
  
