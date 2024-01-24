import os
import sys
import tempfile
import urllib2

sizes = [("thumbnails", 140), ("small", 350), ("medium", 640), ("large", 1024)]

cddir = tempfile.mkdtemp("", "photocd.")

if len(sys.argv) < 3:
    print "Usage: %s <startid> <endid>\n" % sys.argv[0]
    exit

startid = int(sys.argv[1])
endid   = int(sys.argv[2]) + 1 # inclusive bounds

os.mkdir(os.path.join(cddir, "originals"))
for s in sizes:
    os.mkdir(os.path.join(cddir, s[0]))

for imgid in range(startid, endid):
    print "Downloading image %d" % imgid,
    try:
        for s in sizes:
            req = urllib2.Request("http://www.photoprism.net/servlet/image?id=%d&w=%d"
                                  % (imgid, s[1]))
            sock = urllib2.urlopen(req)
            open("%s/%s/%d-%d.jpeg" % (cddir, s[0], imgid, s[1]), "w").write(sock.read())
            print ".",
        print "done."
    except:
        print "Skipping"

lic = open("%s/LICENSE" % cddir, "w")
lic.write("The photographs in this collection are copyrighted by Michael A. Dickerson.\n")
lic.write("This work is provided to you under the Creative Commons Attribution-NonCommercial License.  You may copy, redistribute, modify, or make derivative works using these images, but copies must retain this notice and be attributed to the original author.  Commercial use is not permitted without permission of the author.  To view a copy of this license, visit http://creativecommons.org/licenses/by-nc/2.0/ or send a letter to Creative Commons, 559 Nathan Abbott Way, Stanford, California 94305, USA.\n")
lic.close()

print "Output directory is %s" % cddir                          
