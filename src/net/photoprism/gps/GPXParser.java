package net.photoprism.gps; // Take this off and it compiles just fine

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * GPX is an XML file format for GPS data interchange described here:
 * http://www.topografix.com/GPX/1/0
 *
 * A file may contain zero or more waypoints, zero or more tracks, and
 * zero or more routes.  Each track contains zero or more trackpoints.
 * We only care about waypoints and trackpoints, so we discard
 * everything else.
 *
 * The SAX example I am following is at:
 * http://java.sun.com/j2ee/1.4/docs/tutorial/doc/JAXPSAX3.html#wp64190
 *
 * @author mikey@singingtree.com (Mikey Dickerson)
 * @version 20080422
 */

public class GPXParser extends DefaultHandler
{
  protected GPXTrackLog tracklog;
  protected GPXTrackLog.Timepoint currentpoint;
  protected StringBuffer charbuffer;

  // We get these: <time>2008-04-22T07:28:04Z</time>
  public static SimpleDateFormat dateformat =
    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

  public GPXParser()
  {
    tracklog = new GPXTrackLog();
  }

  public void startElement(String namespaceURI,
      String sName,
      String qName,
      Attributes attrs)
  throws SAXException
  {
    //System.out.println("start element: " + sName + " " + qName);
    if ("wpt".equals(qName)) {
      double lat = Double.parseDouble(attrs.getValue("lat"));
      double lon = Double.parseDouble(attrs.getValue("lon"));
      currentpoint = new GPXTrackLog.Timepoint(lat, lon);
    } else if ("trkpt".equals(qName)) {
      double lat = Double.parseDouble(attrs.getValue("lat"));
      double lon = Double.parseDouble(attrs.getValue("lon"));
      currentpoint = new GPXTrackLog.Timepoint(lat, lon);
    }
    charbuffer = null;
  }

  public void endElement(String namespaceURI,
      String sName, String qName)
  throws SAXException
  {
    if ("time".equals(qName) && currentpoint != null) {
      try {
        // SimpleDateFormat can't parse "Z" as a timezone, so we
        // kludge it to +0000.
        String datestr = charbuffer.toString().replaceAll("Z", "+0000");
        long t = dateformat.parse(datestr).getTime();
        currentpoint.time = t;
      } catch (ParseException ignored) {
        System.out.println("unparseamable: " + charbuffer.toString());
      }
    } else if ("ele".equals(qName) && currentpoint != null) {
      currentpoint.ele = Double.parseDouble(charbuffer.toString());
    } else if ("wpt".equals(qName)) {
      tracklog.addWaypoint(currentpoint);
      currentpoint = null;
    } else if ("trkpt".equals(qName)) {
      tracklog.addTrackpoint(currentpoint);
      currentpoint = null;
    }
    this.charbuffer = null;
  }

  public void characters(char buf[], int offset, int len)
  throws SAXException
  {
    String s = new String(buf, offset, len);
    if (charbuffer == null) {
      charbuffer = new StringBuffer(s);
    } else {
      charbuffer.append(s);
    }
  }

  /**
   * The factory method that turns a File into a GPXTrackLog.
   */

  public static GPXTrackLog parse(File file)
  {
    GPXParser h = new GPXParser();
    SAXParserFactory f = SAXParserFactory.newInstance();
    try {
      SAXParser sp = f.newSAXParser();
      sp.parse(file, h);
    } catch (Throwable t) {
      t.printStackTrace();
    }

    return h.tracklog;
  }

  /**
   * If you have a String path instead, that's ok too.
   */

  public static GPXTrackLog parse(String path)
  {
    return parse(new File(path));
  }

  /**
   * Invoke by itself to test on an arbitrary gpx file.  Will parse
   * the file, then output some simple stuff like the defined time
   * range.
   */

  public static void main(String argv[])
  {
    if (argv.length != 1) {
      System.err.println("must specify a file");
      System.exit(1);
    }

    GPXTrackLog log = parse(argv[0]);

    // System.out.println(h.tracklog.getTrackpoints());

    System.out.println("Parsed " + log.countTrackpoints() 
        + " trackpoints and " + log.countWaypoints()
        + " waypoints.");
    long start = log.getFirst().time;
    System.out.println("first point: " + dateformat.format(new Date(start))
        + " t=" + start);
    long end = log.getLast().time;
    System.out.println("last point: " + dateformat.format(new Date(end))
        + " t=" + end);
    System.out.println("range: " + (end-start)/1000 + "s");
    GPXTrackLog.Timepoint tp = log.timeToTrackpoint((start+end)/2, (long)90*86400*1000);
    System.out.println("midpoint: " + tp);
    System.exit(0);

  }


}
