package net.photoprism.gps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.photoprism.PhotoUtils;

/**
 * Store a given GPX track log in the database, and tag any photos that
 * fall within its time range.
 *
 * @author mikey@singingtree.com (Mikey Dickerson)
 * @version 20080423
 */

public class GPXInserter
{

  public static long ONE_DAY = 86400 * 1000;

  public static void addGpxLog(File logfile, int owner, 
      PrintWriter pw, Connection db)  
  throws SQLException, FileNotFoundException, IOException
  {
    pw.println("parsing gps log: " + logfile);
    
    GPXTrackLog tracklog = GPXParser.parse(logfile);

    StringBuffer sql = new StringBuffer();
    sql.append("INSERT INTO GpsLog (Owner, StartTime, EndTime, Size, "
        + "LogFile) VALUES (");
    sql.append(owner);
    sql.append(", ");
    sql.append(dbTimestamp(tracklog.getFirst().time));
    sql.append(", ");
    sql.append(dbTimestamp(tracklog.getLast().time));
    sql.append(", ");
    sql.append(logfile.length());
    sql.append(", ");
    sql.append(PhotoUtils.quoteString(logfile.getPath()));
    sql.append(");");

    Statement st = db.createStatement();
    st.executeUpdate(sql.toString());

    pw.println("stored at " + logfile.getPath() + " ("
        + PhotoUtils.formatByteCount((int)logfile.length()) + ")");

    // Now look for any photos that might be inside the time range of
    // this track log.  Do any "key" image first, which is one that
    // was synchronized to a GPX waypoint.  This will tell us the error
    // in the camera clock.

    sql = new StringBuffer();
    sql.append("SELECT imageid, "
        + "ts AT TIME ZONE 'UTC' AS utctime, "
        + "gpskey FROM Image "
        + "WHERE image.author=");
    sql.append(owner);
    sql.append(" AND image.ts >= ");
    sql.append(dbTimestamp(tracklog.getFirst().time - ONE_DAY));
    sql.append(" AND image.ts <= ");
    sql.append(dbTimestamp(tracklog.getLast().time + ONE_DAY));
    sql.append(" ORDER BY gpskey, ts;");

    ResultSet rs = st.executeQuery(sql.toString());
    long clockerror = 0;
    int noMatches = 0;
    int count = 0;
    GPXTrackLog.Timepoint tp = null;

    pw.println("min timestamp in gps log is: "
        + dbTimestamp(tracklog.getFirst().time));
    pw.println("max timestamp in gps log is: "
        + dbTimestamp(tracklog.getLast().time));

    // We get this: "2011-06-01 18:36:41"
    SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    
    while (rs.next()) {

      ++count;
      int imgid = rs.getInt("imageid");
      
      // Tried a billion variations of this.  The only one that seems to work
      // reliably is to force the database to output a string in UTC, then
      // force java to re-parse it as UTC.
      String utctime = rs.getString("utctime");
      long t = 0;      
      try {
        t = dateformat.parse(utctime + "+0000").getTime();
      } catch (ParseException e) {
        pw.println("unparseable date from database: " + utctime);
        continue;
      }
      
      pw.println("image " + imgid + " has t=" + t + " " + dbTimestamp(t));
      tp = null;

      if (rs.getBoolean("gpskey")) {
        // This is a key image; try to pair with a waypoint.
        tp = tracklog.timeToWaypoint(t, 70 * 60 * 1000);
        if (tp == null) {
          pw.println("could not pair key image " + imgid
              + " with a waypoint (t=" + t + ")");

        } else {
          // Successfully paired; set clock offset and use this
          // timepoint.
          clockerror = tp.time - t;
          pw.println("paired key image " + imgid + " with waypoint.  "
              + "New clock error is " + clockerror/1000 + "s.");
          t = tp.time;
        }
      }

      if (tp == null) {
        // Not a key image, or failed to pair with waypoint.  Apply
        // clock offset, then look for nearest trackpoint up to 2h away.
        t += clockerror;
        tp = tracklog.timeToTrackpoint(t, 7200 * 1000);
      }

      if (tp == null) {
        ++noMatches;
      } else {
        sql = new StringBuffer();
        sql.append("UPDATE Image SET Latitude=");
        sql.append(tp.lat);
        sql.append(", Longitude=");
        sql.append(tp.lon);
        sql.append(", Altitude=");
        sql.append(tp.ele);
        sql.append(" WHERE Image=");
        sql.append(imgid);
        sql.append(';');
        Statement upd = db.createStatement();
        upd.executeUpdate(sql.toString());
        pw.println("geotagged image " + imgid
            + " (" + tp.lat + ", " + tp.lon + ")");
        if (clockerror != 0) {
          sql = new StringBuffer();
          sql.append("UPDATE image SET ts=ts + INTERVAL '");
          sql.append(clockerror / 1000);
          sql.append(" seconds' WHERE imageid=");
          sql.append(imgid);
          sql.append(';');
          upd.executeUpdate(sql.toString());
          pw.println("adjusted timestamp on image " + imgid);
        }
      }
    } // while rs.next()

    pw.println("Compared " + count + " images.");
    pw.println("No matching trackpoints for " + noMatches + " of them.");
  }

  private static String dbTimestamp(long t)
  {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    return PhotoUtils.quoteString(df.format(new Date(t)));
  }


  private static void usage()
  {
    System.err.println("Usage: GPXInserter file owner");
    System.err.println("where file is a GPX track log, and owner is the "
        + "int PersonID that owns the file.");
    System.exit(1);
  }

  /**
   * When run on our own, process a single file and an owner.
   */

  public static void main(String argv[])
  {

    if (argv.length != 2) usage();

    Connection db = null;
    PrintWriter pw = new PrintWriter(System.out, true);
    int owner = 0;
    File logfile = new File(argv[0]);

    try { owner  = Integer.parseInt(argv[1]); }
    catch (NumberFormatException e) { usage(); }

    try {
      db = PhotoUtils.getConnection();
      addGpxLog(logfile, owner, pw, db);
      db.close();
    } catch (ClassNotFoundException e) {
      pw.println("unable to load driver: " + PhotoUtils.JDBC_DRIVER);
      System.exit(2);
    } catch (SQLException e) {
      pw.println("database error: " + e.getMessage());
      System.exit(3);
    } catch (FileNotFoundException e) {
      pw.println(e);
      System.exit(4);
    } catch (IOException e) {
      pw.println(e);
      System.exit(5);
    }

  }


}
