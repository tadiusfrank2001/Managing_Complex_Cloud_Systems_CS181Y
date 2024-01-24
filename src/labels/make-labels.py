#!/usr/bin/python
import os
import pgdb
import sys

EPSILON = 0.001
MIN_PPI = 150
SIZES = ((20,30),(16,20),(11,14),(8,10),(5,7),(4,6))

db = pgdb.connect(database='photo', host='nightfall', user='photoprism')
st = db.cursor()

sql = 'SELECT imageid, width, height, originalfile, date FROM image WHERE %s;' % sys.argv[1]
st.execute(sql)
if st.rowcount == 0:
    print "Error: No rows returned."
    exit

print '''
\documentclass{letter}
\usepackage{graphicx}

\usepackage[avery5163label,noprintbarcodes,nocapaddress]{envlab}
\makelabels

\\begin{document}
\startlabels

'''

row = st.fetchone()
while row:
    # try to fetch the image file
    url = 'http://www.photoprism.net/servlet/image?id=%d&w=250' % row[0]
    cmd = 'wget -q -O %d.jpg \'%s\'' % (row[0], url)
    print '% trying to execute: ' + cmd
    os.system(cmd)
    
    # try to figure out the optimum printing size.  First check whether the
    # aspect ratio has been changed from 3:2, in which case it was deliberately
    # cropped.
    (w, h) = (row[1], row[2])
    if w > h: # wide image
        (long, short) = (w, h)
    else: # tall image
        (long, short) = (h, w)
    r = float(short) / float(long)
    if long == 1800 and short == 1215:
        # special case: this is a (cheap) 35mm film scan, which we know we can scan
        # at higher resolution, at least 3833x2564, if we want
        prn = "16x20in"
    elif abs(r - 8.0/10.0) < EPSILON:
        # image was deliberately cropped to 8x10
        prn = "8x10in"
    elif abs(r - 5.0/7.0) < EPSILON:
        # image was deliberately cropped to 5x7
        prn = "5x7in"
    else:
        # image was not cropped, or was cropped to 4x6, or was cropped to
        # something weird: estimate maximum print size by number of pixels
        for s in SIZES:
            if short >= s[0] * MIN_PPI and long >= s[1] * MIN_PPI:
                prn = '%dx%din' % s
                break # assumes SIZES sorted largest to smallest
        else:
            prn = '(image too small)'

    print '\printreturnlabels{1}{%'
    print '\\begin{minipage}{27mm}'
    if w > h:
        print '\includegraphics[height=25mm,angle=270]{%s}\end{minipage}' % row[0]
    else:
        print '\includegraphics[width=25mm]{%s}\end{minipage}' % row[0]
    print '\\begin{minipage}{70mm}{\sffamily%'
    print 'Proof number: %s \\\\' % row[0]
    if row[3]: print 'Negative: %s \\\\' % row[3]
    print 'Taken: %s \\\\' % row[4]
    print 'Prints well at: %s \\\\' % prn
    print '\\\\'
    print 'For information please contact: \\\\ mikey@singingtree.com \\\\'
    print '\\copyright~2005 Michael A. Dickerson'
    print '}\\end{minipage}'
    print '}'

    row = st.fetchone()
    
print '\end{document}'
