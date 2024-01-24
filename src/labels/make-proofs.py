import glob
import Image
import ImageDraw
import ImageFont

printsize = (6, 4)
printres = 300
border = 10
font = ImageFont.truetype("../Bitstream-Vera-Sans-Mono-Bold.ttf", 64)

n = 0
proof = None
proofsize = tuple(map(lambda x: x * printres, printsize))
halfsize = tuple(map(lambda x: x * printres / 2, printsize))
subsize = tuple(map(lambda x: x - border*2, halfsize))

def saveproof(proof):
    name = "proof%03d.png" % ((n + 1) / 4)
    proof.save(name)
    print "Wrote to %s" % name

files = glob.glob("*.jpg")
files.sort()

for infile in files:
    if proof == None:
        # create a new buffer
        proof = Image.new("RGB", proofsize)
        print "Created new buffer (%dx%d)" % proofsize

    im = Image.open(infile)

    im = im.resize(subsize)
    if im.size[1] > im.size[0]: im = im.transpose("ROTATE_90")

    if   n % 4 == 0: coord = (0,0)
    elif n % 4 == 1: coord = (0, halfsize[1])
    elif n % 4 == 2: coord = (halfsize[0], 0)
    elif n % 4 == 3: coord = halfsize
    coord = tuple(map(lambda x: x + border, coord))
    
    draw = ImageDraw.Draw(im)
    textsize = font.getsize(infile[:-4])
    textcoord = tuple([subsize[x] - textsize[x] - border for x in (0,1)])
    draw.text(textcoord, infile[:-4], font=font)
    
    proof.paste(im, coord)
    print "Pasted %s at %s" % (infile, coord)
    if n % 4 == 3:
        saveproof(proof)
        proof = None
    n += 1

if proof != None:
    while n % 4 < 3: n += 1
    saveproof(proof)




    
        


