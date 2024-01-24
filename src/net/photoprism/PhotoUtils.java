package net.photoprism;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * class with utility methods convenient to the various photo database
 * programs.
 *
 * <p>Copyright &copy; 2001-2005 Michael A. Dickerson</p>
 *
 * @author Michael A. Dickerson
 * @version 2005-08-19
 */

public class PhotoUtils
{

  /**
   * parses a database formatted date and time string to create
   * a valid Date
   *
   * @param date String date
   * @param time String time
   * @return Date correctly initialized
   */

  public static Date parseDateTime(String date, String time) {

    String format = "";
    String s = "";
    if (date != null) {
      format += DATE_FORMAT;
      s += date;
    }
    if (time != null) {
      if (format.length() > 0) {
        format += " ";
        s += " ";
      }
      format += TIME_FORMAT;
      s += time;
    }

    if (format.length() == 0) return null;

    SimpleDateFormat fmt = new SimpleDateFormat(format);
    return fmt.parse(s, new ParsePosition(0));

  }

  /**
   * converts a database formatted date field to a more attractive
   * format.
   *
   * @param date String in database format
   * @return String such as "20 Sep 2000 (Wed)"
   */

  public static String friendlyDate(String date) {

    Date d = parseDateTime(date, null);
    SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy (EE)");
    return fmt.format(d);

  }

  /**
   * make string s safe to embed in a SQL quoted string.
   */

  public static String sanitizeStr(String s)
  {
    if (s == null) return s;
    StringBuffer b = new StringBuffer();
    char c[] = s.toCharArray();
    for (int i = 0; i < c.length; i++) {
      switch(c[i]) {
      case '\'':
        b.append("''");
        break;
      case '\n':
        b.append("\n");
        break;
      case '\\':
        b.append("\\");
        break;
      case '\t':
        b.append("\t");
        break;
      case '\r':
        b.append("\r");
        break;
      case '\b':
        b.append("\b");
        break;
      default:
        b.append(c[i]);
      }
    }
    return b.toString();
  }

  /**
   * make an int safe to embed in a SQL query.
   */

  public static String sanitizeInt(String s)
  {
    if (s == null) return s;
    try {
      Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return "null";
    }
    return s;
  }

  /**
   * quotes a string value in database-ready format.  SQL requires
   * strings to be delimited with single quotes, and literal single
   * quotes are specified by two in a row.
   *
   * <p>For example Mikey's Program is quoted as 'Mikey''s Program'</p>
   *
   * @param s String to quote
   * @return String ready to use in SQL
   */

  public static String quoteString(String s) {

    // return "null" if s == null
    if (s == null) return "null";

    // return the string "null" if s is empty
    if (s.length() == 0) return "null";

    // also return the string "null" if s == "null", this avoids
    // storing the string value 'null' in the database
    if (s.equalsIgnoreCase("null")) return "null";

    // trim leading and trailing whitespace from s
    s = s.trim();

    return "'" + sanitizeStr(s) + "'";

  }

  /**
   * a handy FilenameFilter that picks out images we can understand.
   * As of January 2020, this is just good old jpeg.
   */

  public static class ImageFilter implements FilenameFilter {

    public ImageFilter() { }

    public boolean accept(File dir, String name)
    {
      return accept(name);
    }

    // this one isn't required to implement FilenameFilter, but
    // is useful nonetheless

    public boolean accept(String name)
    {
      String n = name.toLowerCase();
      return n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

  }

  /**
   * generates the path to the original image file on disk, given
   * ImageID.
   * 
   * @param imgID int ImageID of the new image
   * @return File a place to write the new image
   */

  public static File getImageFile(int imgID)
  {
    StringBuffer path = new StringBuffer();

    path.append(IMAGE_ROOT);
    path.append('/');
    path.append(imgID % 10);
    path.append('/');
    path.append((imgID / 10) % 10);
    path.append('/');
    path.append(imgID);
    // if format is not jpeg, the name on disk will be deceptive.  Sorry.
    path.append(".jpg");
    return new File(path.toString());
  }

  /**
   * makes up a name for an uploaded file, assuming you know the
   * owner.  The returned File is guaranteed not to exist at the time
   * getNewUploadFile() was called, so there is a race condition here,
   * so don't try to upload from thirty different browsers at once.
   * The path where the File would be created is created if it doesn't
   * exist.
   * 
   * @param owner PersonID that owns the uploaded file, use '0' if
   * there is no owner in context.
   * @param orig_name String suggested file name (incoming files already have
   * names, so it's a little nicer to save e.g. '2010-07-04.gpx.1' on disk
   * than 'up-8-0044')
   * @return File to save to
   */

  public static File getNewUploadFile(int owner, String orig_name)
  throws IOException
  {
    File path = new File(IMAGE_ROOT + "/upload/" + owner);
    if (!path.isDirectory() && !path.mkdirs()) {
      throw new IOException("could not create path: " + path);
    }
    // keep only the base file name from orig_name, which may have
    // come in with path components ('dir/dir/some_file.jpg')
    String parts[] = orig_name.split("/");
    return uniqueFile(path, parts[parts.length - 1]);
  }
  
  /**
   * use if you have a desired path and file name, but want to avoid
   * clobbering anything already there.
   * 
   * @param parent File "/path/to/destination/"
   * @param basename String desired file name, may become "x.jpeg.1"
   * @return
   */
  
  private static File uniqueFile(File parent, String basename)
  {
    int num = 0;
    File f = new File(parent, basename);
    while (f.exists()) {
      f = new File(parent, basename + "." + num++);
    }
    return f;
  }
  
  /**
   * gets a new ImageID from the ImageID sequence generator.  Since
   * the ImageID is used to determine the file name, among other
   * things, it is useful to have a priori knowledge of what that
   * number is going to be.
   *
   * @param db Connection to photo database
   * @return int new ImageID from the sequence generator
   * @exception SQLException if things go funny
   */

  public static int getNewImageID(Connection db) throws SQLException {
     Statement st = db.createStatement();
     ResultSet rs = st.executeQuery("SELECT nextval ('seq_imageid');");
     int id = 0;
     if (rs.next()) id = rs.getInt(1);
     rs.close();
     st.close();
     return id;
  }

  /**
   * attempts to create a connection to the photo database.
   *
   * @exception SQLException if connection failed
   */

  public static Connection getConnection() 
  throws SQLException {
     // this clunky crap is still being created by the Java brain
     // trust.  Class names (prepended with magic words) passed as
     // String arguments, the return value is wrong and has to be
     // cast, 45 lines of XML boilerplate that have to be just right
     // in multiple magic-named files, etc etc etc
     try {
        InitialContext ct = new InitialContext();
        DataSource ds = (DataSource)ct.lookup("java:/comp/env/jdbc/postgres");
        return ds.getConnection();
     } catch (NamingException e) {
        throw new SQLException("lol what is a NamingException: " + e);
     }
  }

  /**
   * copies a file, not surprisingly.  Horribly inefficient but
   * did not take long to write.
   *
   * @exception FileNotFoundException as needed
   * @exception IOException if reading or writing fails for some reason
   */

  public static void copyFile(File src, File dest) 
  throws FileNotFoundException, IOException
  {
    File dir = new File(dest.getParent());
    if (!dir.isDirectory()) dir.mkdirs();

    /*
	19 Aug 05 MAD: something about the ImageInserter process was slow
	               as hell; doing the reads 1024 bytes at a time made a
	               big difference.
    6 Jan 20 MAD: I think we can afford 4k of buffer space nowadays
    */

    FileInputStream in = new FileInputStream(src);
    FileOutputStream out = new FileOutputStream(dest);
    byte[] b = new byte[4096];
    while (true) {
      int bytes = in.read(b);
      if (bytes == -1) break;
      out.write(b, 0, bytes);
    }
    out.close();
    in.close();
  }

  /**
   * method to estimate the final compressed size of a JPEG knowing
   * only the dimensions.  Obviously this is going to be very rough.
   * I collected some data on 11 Sep 2004 for purposes of determining
   * a model:
   *
   * 140x105 =  14700px, avg  5198 b => px*0.35360=b (3135 samples)
   * 250x187 =  46750px, avg 13267 b => px*0.28378=b (361 samples)
   * 450x337 = 151650px, avg 31939 b => px*0.21060=b (2396 samples)
   *
   * Obviously this is not linear, probably because bigger images
   * have more smooth area that compresses easily.  So this simple
   * linear approximation will tend to underestimate small files and
   * overestimate large ones.
   *
   * @param w int pixel width (doesn't matter which dimension really)
   * @param h int pixel height
   * @return int estimated bytes in JPEG file
   */

  public static int estimateJpegSize(int w, int h)
  {
    double est = 0.25 * (double)(w * h);
    return (int)est;
  }

  /**
   * method to convert a byte count to a more easily understood
   * approximation (e.g. "1.15 MB" instead of 1165325).
   *
   * @param b int number of bytes
   * @return String suitable for display
   */

  public static String formatByteCount(int b)
  {
    StringBuffer s = new StringBuffer();
    if (b > GB) {
      s.append(b / GB);
      s.append(" Gb");
    } else if (b > MB) {
      s.append(b / MB);
      s.append(" Mb");
    } else if (b > KB) {
      s.append(b / KB);
      s.append(" kb");
    } else {
      s.append(b);
    }
    return s.toString();
  }

   /**
   * Send an RPC to the pkeep process, which probably manages an S3 backend.
   * 
   * @param id imageid
   * @param size length of longer edge, in pixels
   * @param inst description of rendering instructions understood by pkeep
   * @return whatever text pkeep sends back, hopefully a URL
   */

  public static String askPkeep(int id, int size, String inst) 
  throws IOException, SocketException, UnknownHostException {

    // Ask pkeep for a URL.
    DatagramSocket s = new DatagramSocket();
    s.setSoTimeout(60000); // Be patient.

    StringBuffer req = new StringBuffer();
    req.append(4747); // TODO: ticket number is presently ignored
    req.append(',');
    req.append(id);
    req.append(',');
    req.append(size);
    req.append(',');
    req.append(inst);
    byte[] buf = req.toString().getBytes();

    DatagramPacket p = new DatagramPacket(buf, buf.length,
        InetAddress.getByName(PKEEP_HOST), 4770);
    s.send(p);

    p.setData(new byte[1024]);
    s.receive(p);
    String msg = new String(p.getData(), 0, p.getLength());
    if (!msg.startsWith("4747,")) {
      throw new IOException("ticket number mismatch from pkeep");
    }
    return msg.substring(5);
  }

  // These are the choices the UI will offer in time zone fields.  Choose
  // from: http://www.postgresql.org/docs/8.0/static/datetime-keywords.html
  public static final String TIMEZONES = 
    "US/Pacific,US/Mountain,US/Central,US/Eastern," +
    "Europe/Dublin,UTC," + 
    "GMT-12,GMT-11,GMT-10,GMT-9,GMT-8,GMT-7,GMT-6,GMT-5,GMT-4,GMT-3,GMT-2," +
    "GMT-1,GMT,GMT+1,GMT+2,GMT+3,GMT+4,GMT+5,GMT+6,GMT+7,GMT+8,GMT+9,GMT+10," +
    "GMT+11,GMT+12";

  public static final double EPSILON = 0.00001;

  public static final String PKEEP_HOST = "localhost";

  public static final String IMAGE_ROOT = "/srv/photo/pkeep_orig";
  public static final String TRASH_PATH = IMAGE_ROOT + "/trash";

  public static final int WM_NONE = 1;
  public static final int WM_COPYRIGHT = 2;
  public static final int WM_PROOF = 3;
  public static final int WM_CAPTION = 4;
  public static final int WM_USERNAME = 5;

  public static final String DATE_FORMAT = "yyyy-MM-dd";
  public static final String TIME_FORMAT = "HH:mm:ss";

  public static final int KB = 1024;
  public static final int MB = 1024 * 1024;
  public static final int GB = 1024 * 1024 * 1024;

  public static final int UPLOAD_MAX_SIZE = MB * 512;

  public static final String VERSION="$Id$";

}





