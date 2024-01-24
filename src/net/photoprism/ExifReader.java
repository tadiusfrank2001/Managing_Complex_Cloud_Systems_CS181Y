package net.photoprism;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Program to extract Exif information and add it to the photo
 * database.  When executed directly, it looks for images that are of 
 * the 'Digital Photo' type with NewImage == true.  Also exposes the
 * useful method updateExif(), which can be called to process a specified
 * image.  This will probably fail if the named image already has a row
 * in the Photo table.
 *
 * <p>Relies on the public domain jhead program by Matthias Wandel
 * rather than attempting to interpret the Byzantine exif structure.</p>
 *
 * <p>3 Apr 02 MAD: version 1.1 interprets the Date/Time field and
 * updates the Image table.  This should be an improvement over relying
 * on the file modification time.</p>
 *
 * <p>10 Sep 04 MAD: fixed bug causing shutter and metering fields to
 * be lost (because their names got cut off in parsing)</p>
 *
 * <p>Copyright &copy; 2001-2004 Michael A. Dickerson</p>
 *
 * @author Michael A. Dickerson
 * @version 20040910
 */

public class ExifReader
{


	public static void main(String args[]) {

      // If you got here by running this class directly, you probably
      // wanted the debug mode.
      debug = true;
		System.out.println("Debug mode: no changes will be made.");

      try {
         Connection db = PhotoUtils.getConnection();

         if (args.length > 0) {
            for (String arg : args) { updateExif(arg, db); }
         } else {
            // run a query for everything with newimage=true
            ResultSet rs = db.prepareStatement
               ("SELECT imageid FROM image WHERE newimage=TRUE;")
               .executeQuery();
            while (rs.next()) { updateExif(rs.getInt(1), db); }
            rs.close();
         }

         db.close();

      } catch (IOException | SQLException e) {
         System.out.println("*** exception raised: " + e);
      }

	}

	/**
	 * updateExif() runs the jhead program and updates the database
	 * for one specified image.
	 *
	 * @param imageID which image to update
    * @param db a database connection
    * @throws IOError if the problem was with jhead or pkeep
    * @throws SQLError if the problem was the database
	 */

   public static void updateExif(int imageID, Connection db)
   throws IOException, SQLException
	{
		String imgfile;

      imgfile = PhotoUtils.askPkeep(imageID, 0, "");
		if (!imgfile.startsWith("file://")) {
         throw new IOException("no access to original file");
		}
		imgfile = imgfile.substring(7);

		Process jhead = null;
		int exit_status = 0;

		try {
			String jhead_cmd = JHEAD + " " + imgfile;
			if (debug) System.out.println("running " + jhead_cmd);
			jhead = Runtime.getRuntime().exec(jhead_cmd);
			exit_status = jhead.waitFor();
		} catch (InterruptedException e) {
         throw new IOException("interrupted waiting for jhead: " + e);
		}

		if (exit_status != 0) {
         throw new IOException("jhead exit status " + exit_status);
		}
		BufferedReader in = new BufferedReader
		(new InputStreamReader(jhead.getInputStream()));

		// we use buffers to construct composite fields that
		// include more than one jhead output line

		String newTimestamp = null;
		String fileTimestamp = null;
		StringBuffer camera = new StringBuffer();
		StringBuffer exposure = new StringBuffer();
		ArrayList<String> imageFields = new ArrayList<String>();
      StringBuffer sql = null;

      while (true) {
         String l = in.readLine();
         if (l == null) break;

         if (debug) System.out.println(l);

         String field = "";
         String arg = "";
         int delim = l.indexOf(':');

         if (delim >= 0) {
            field = l.substring(0, delim).toLowerCase().trim();
            if (l.length() > delim + 2)
               arg = l.substring(delim + 2).trim();
         } else {
            continue;
         }

         // For some fields, we collect multiple jhead output lines:

         if (field.startsWith("camera make")) {
            // special case the silly Pentax descriptor
            if (arg.equals("PENTAX Corporation")) arg = "PENTAX";
            if (camera.length() > 0) camera.append(' ');
            camera.append(arg);
         } else if (field.startsWith("camera model")) {
            // Some manufacturers (Pentax, Kodak) repeat the
            // manufacturer name in "camera model," and others don't.
            // So if the camera model we are about to append
            // duplicates the manufacturer we already have, drop it.
            if (arg.startsWith(camera.toString())) {
               camera = new StringBuffer();
            }
            if (camera.length() > 0) camera.append(' ');
            camera.append(arg);
         } else if (!field.equals("exposure time") &&
                    field.startsWith("exposure")) {
            // this actually catches several fields such as
            // Exposure time, Exposure bias, Exposure
            if (exposure.length() > 0) exposure.append(' ');
            exposure.append(arg);
         } else if (field.startsWith("date/time")) {
            // this field looks like '2002:03:11 00:30:01';
            // the format appears to be the same across cameras
            // (well not really, but jhead pretends).
            if (arg.length() < 12) {
               if (debug) System.out.println("date/time field too short");
            } else {
               newTimestamp = arg.substring(0, 10).replace(':', '-')
						+ arg.substring(10);
            }
         } else if (field.startsWith("file date")) {
            // 9 Feb 05 MAD: use file date as a backup if Date/Time
            // is missing or bogus
            if (arg.length() < 11) {
               if (debug) System.out.println("file date field too short");
            } else {
               fileTimestamp = arg.substring(0, 10).replace(':', '-')
						+ " " + arg.substring(10);
            }

         // The rest are simple 1:1 translations.

         } else if (field.startsWith("iso")) {
            imageFields.add("film=" + PhotoUtils.quoteString("ISO " + arg));
         } else if (field.startsWith("flash")) {
            imageFields.add("flash=" +
                       (arg.toLowerCase().startsWith("y") ? "true" : "false"));
         } else if (field.startsWith("focal length")) {
            imageFields.add("focallength=" + PhotoUtils.quoteString(arg));
         } else if (field.equals("exposure time")) {
            imageFields.add("shutter=" + PhotoUtils.quoteString(arg));
         } else if (field.startsWith("metering mode")) {
            imageFields.add("metering=" + PhotoUtils.quoteString(arg));
         } else if (field.startsWith("jpeg process")) {
            // 9 Feb 05 MAD: Note that jhead 2.3 doesn't seem to produce
            // this field
            imageFields.add("process=" + PhotoUtils.quoteString(arg));
         } else if (field.startsWith("jpeg quality")) {
            imageFields.add("process=" + PhotoUtils.quoteString
                            ("jpeg q=" + arg));
         } else if (field.startsWith("aperture")) {
            imageFields.add("aperture=" + PhotoUtils.quoteString(arg));
         } else if (field.equals("orientation")) {
            if (arg.equals("rotate 90")) {
               // yes, jhead's idea of rotation is the opposite of ours
               imageFields.add("rotation=270");
            } else if (arg.equals("rotate 180")) {
               imageFields.add("rotation=180");
            } else if (arg.equals("rotate 270")) {
               imageFields.add("rotation=90");
            } else{
               if (debug) System.out.println("bad orientation arg: " + arg);
            }
         } else if (field.equals("resolution")) {
            String[] args = arg.split(" x ");
            if (args.length == 2) {
               imageFields.add("width=" + args[0]);
               imageFields.add("height=" + args[1]);
            } else {
               if (debug) System.out.println("bad resolution arg: " + arg);
            }
         } else if (field.equals("gps latitude")) {
            try { imageFields.add("latitude=" + parseLatLon(arg)); }
            catch (NumberFormatException e) {
               // you will get trash strings like "? ?" sometimes, and
               // it seems best to just keep going.
               if (debug) System.out.println("bad latitude arg: " + arg);
            }
         } else if (field.equals("gps longitude")) {
            try { imageFields.add("longitude=" + parseLatLon(arg)); }
            catch (NumberFormatException e) {
               if (debug) System.out.println("bad longitude arg: " + arg);
            }
         } else if (field.equals("gps altitude")) {
            try { imageFields.add("altitude=" + parseAltitude(arg)); }
            catch (NumberFormatException e) {
               if (debug) System.out.println("bad altitude arg: " + arg);
            }
         }

      } // while (in.readline())

      // do final cooking of field values
      if (exposure.length() > 0) {
         imageFields.add("exposure=" +
                         PhotoUtils.quoteString(exposure.toString()));
      }
      if (camera.length() > 0) {
         imageFields.add("camera=" +
                         PhotoUtils.quoteString(camera.toString()));
      }
      // 9 Feb 05 MAD: looks like some cameras (Laura's Nikon E2100)
      // return 0000-00-00 when the clock isn't set; postgres doesn't
      // like this
      if (newTimestamp == null || newTimestamp.startsWith("0000-00-00"))
         newTimestamp = fileTimestamp;
      if (newTimestamp != null && !newTimestamp.startsWith("0000-00-00")) {
         newTimestamp = "TIMESTAMP " + PhotoUtils.quoteString(newTimestamp);
         imageFields.add("ts=" + newTimestamp);
      }

      // did we find anything?
      if (imageFields.size() == 0) return;

      // assemble SQL statement
      sql = new StringBuffer();
      sql.append("UPDATE image SET ");
      sql.append(join(imageFields, ", "));
      sql.append(" WHERE imageid=");
      sql.append(imageID);
      sql.append(';');

      if (debug) {
         System.out.println(sql.toString());
      } else {
         db.createStatement().executeUpdate(sql.toString());
      }

      in.close();
	}

	/**
	 *  Yes, Java seems to really not have this.
	 *  
	 * @param l ArrayList of random Strings
	 * @param delimiter, probably ", ", to put between them
	 * @return join(["your", "mom"], ", ") => "your, mom"
	 */

	public static String join(ArrayList<String> l, String delimiter) {
		StringBuffer b = new StringBuffer();
		Iterator<String> i = l.iterator();
		if (i.hasNext()) {
			b.append(i.next());
			while (i.hasNext()) {
				b.append(delimiter);
				b.append(i.next());
			}
		}
		return b.toString();		
	}

	/**
	 * you might have the imageID as a string (likely if it was a servlet
	 * parameter)
	 *
	 * @param imageID which image to update, in String form
	 * @param pw PrintWriter to send commentary to
	 * @return true if successful
	 */

   public static void updateExif(String imageID, Connection db)
   throws SQLException, IOException
	{
		int i = 0;
		try {
			i = Integer.parseInt(imageID);
		} catch (NumberFormatException e) {
         throw new IOException("bad imageid: " + imageID);
		}
		updateExif(i, db);
	}

   /**
    * turn the jhead strings such as 'N 42d 31.454700m  0s' into
    * doubles.
    */

   public static double parseLatLon(String s) throws NumberFormatException
   {
      // example from Canon 6D Mark II : 'N 39d 30.092899m  0s'
      // example from LGE Nexus 5      : 'N 38d 59m 33.2345s'
      Pattern p = Pattern.compile
         ("([NSEW])\\s+([0-9\\.]+)d\\s+([0-9\\.]+)m\\s+([0-9\\.]+)s");
      Matcher m = p.matcher(s);
      double deg = 0;
      if (m.matches()) {
         deg = Double.parseDouble(m.group(2));
         deg += Double.parseDouble(m.group(3)) / 60.0;
         deg += Double.parseDouble(m.group(4)) / 3600.0;
         if (m.group(1).equals("S") || m.group(1).equals("W")) deg *= -1;
      } else {
         throw new NumberFormatException();
      }
      return deg;
   }

   /**
    * turn the jhead string ' 376.00m' into a double.
    */

   public static double parseAltitude(String s) throws NumberFormatException
   {
      Pattern p = Pattern.compile("([0-9\\.]+)m");
      Matcher m = p.matcher(s);
      if (m.find()) {
         return Double.parseDouble(m.group(1));
      } else {
         throw new NumberFormatException();
      }
   }

	private static boolean debug = false;
	private static final String JHEAD = "/usr/bin/jhead";

}

