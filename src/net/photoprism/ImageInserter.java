package net.photoprism;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.photoprism.PhotoUtils.ImageFilter;

/**
 * A class to load images into the photo database, reading the DCIM directory
 * structure supplied by my digital camera and at least some others.  Can
 * be executed directly, with path and author as command line arguments.  Also
 * exposes methods like addImage() for other programs to use as they see fit.
 *
 * <p>Copyright &copy; 2001 Michael A. Dickerson</p>
 *
 * @author Michael A. Dickerson
 * @version 20110709
 */

public class ImageInserter
{

  /**
   * main() just validates the command line arguments and passes the
   * request to addDCIM()
   *
   * @see #addDCIM
   * @param args the usual
   * @return will exit nonzero on a fatal error
   */

  public static void main(String args[])
  {
    if (args.length != 2) usage();

    Connection db = null;
    PrintWriter pw = new PrintWriter(System.out, true);
    int author = 0;
    File root = new File(args[0]);

    try { author  = Integer.parseInt(args[1]); }
    catch (NumberFormatException e) { usage(); }

    try {
      db = PhotoUtils.getConnection();
    } catch (SQLException e) {
      pw.println("couldn't open database: " + e.getMessage());
      System.exit(3);
    }

    if (root.isDirectory()) {
      addDCIM(root, author, pw, db);
    } else if (root.toString().toLowerCase().endsWith(".zip")) {
      addZip(root, author, null, pw, db);
    } else {
      addImage(root, root.getName(), null, author, pw, db);
    }

    try { db.close(); }
    catch (SQLException ignored) { }

  }

  /**
   * method to print usage instructions and quit.  WATCH OUT, this
   * will call System.exit(), which is likely to bring down the
   * entire JVM.  This makes sense when ImageInserter is being run
   * on its own, but probably not from inside a servlet.
   */

  private static void usage()
  {
    System.err.println("Usage: ImageInserter (<dir>|<file>) <author>");
    System.err.println("where <dir> is the root of a DCIM directory tree" 
        + " that came from a digital camera,");
    System.err.println("<file> is the name of a single image file or an"
        +"archive (must end with .zip),");
    System.err.println("and <author> is the PersonID of the owner "
        + "of these pictures.");
    System.exit(1);
  }

  /**
   * reads a DCIM directory structure and adds all the images it finds.
   * The given path should be the root of the DCIM tree.  This procedure
   * will look in each directory contained in the root for JPEG images,
   * and add anything found to the database.
   *
   * @param root File representing the top of the DCIM tree
   * @param author PersonID to become the owner of these images
   * @param pw PrintWriter that will receive anything we have to say
   * @param db Connection, to save us the trouble of opening another
   * @return nothing
   */

  public static void addDCIM(File root, int author, 
      PrintWriter pw, Connection db)
  {
    pw.println("adding DCIM directory: " + root.toString());

    if (!root.isDirectory()) {
      pw.println("*** specified path is not a directory.");
      return;
    }

    String files[] = root.list();
    if (files == null) {
      pw.println("*** bad directory");
      return;
    }

    for (int i = 0; i < files.length; ++i) {
      File dir = new File(root, files[i]);
      if (dir.isDirectory()) {
        pw.println("Checking subdirectory " + dir);
        String images[] = dir.list(new PhotoUtils.ImageFilter());

        for (int j = 0; j < images.length; j++) {
          File image = new File(dir, images[j]);
          addImage(image, image.getName(), null, author, pw, db);
        }
      } else {
        //System.out.println("Skipping non-directory " + dir);
      }
    }

  }

  /**
   * adds a single image to the database.  The file will be copied to
   * the database's storage space, so you should probably delete your
   * copy after.
   *
   * @param imgfile File to add (probably a temp name)
   * @param origname Original name of file
   * @param timezone String time zone to interpret embedded timestamp
   * @param author PersonID to assign to
   * @param pw PrintWriter to send commentary to
   * @param db Connection that you want us to use
   * @return int newly assigned imageID, or 0 if failed
   */

  public static int addImage(File imgfile, String origname, String timezone, 
      int author, PrintWriter pw, Connection db)
  {
    pw.println("  adding image: " + imgfile);

    long date = imgfile.lastModified();
    int imgID;
    try { imgID = PhotoUtils.getNewImageID(db); }
    catch (SQLException e) {
      pw.println("*** ImageID generation failed: " +
          e.getMessage());
      return 0;
    }

    File copy = PhotoUtils.getImageFile(imgID);

    pw.println("  will copy to:" + copy);

    try {
      PhotoUtils.copyFile(imgfile, copy);
    } catch (Exception e) {
      pw.println("*** file copy failed: " + e); 
      return 0;
    }

    try {
      StringBuffer b = new StringBuffer();
      PreparedStatement st = db.prepareStatement
        ("INSERT INTO Image (ImageID, Author, " +
         "ts, timezone, ImageFile, NewImage, Size, OriginalFile) " +
         "VALUES (?, ?, ?, ?, ?, ?, ?, ?);");
      st.setInt(1, imgID);
      st.setInt(2, author);
      // Note that we don't attempt to timezone-interpret the file timestamp,
      // which is probably about to be overwritten in updateExif().
      st.setTimestamp(3, new java.sql.Timestamp(date));
      st.setString(4, timezone);
      st.setString(5, copy.toString());
      st.setBoolean(6, true);
      st.setLong(7, copy.length());
      st.setString(8, origname);
      st.executeUpdate();
      st.close();
    } catch (SQLException e) {
      pw.println("*** database error: " + e.getMessage());
      return 0;
    }

    // We have the Exif extraction program to populate
    // the Photo table now
    // 3 Apr 02 MAD: but it would still be more convenient 
    //               to do it here
    try {
       ExifReader.updateExif(imgID, db);
    } catch (IOException | SQLException e) {
       pw.println("*** exif update failed: " + e.getMessage());
    }
    return imgID;
  }

  /**
   * add image in the new style, which does not have an "author"
   * but does have a "tag" associated with it.
   */

  public static int addImageByTag
    (File imgfile, String origname, String timezone, int tag,
     PrintWriter pw, Connection db)
  {
    // we only change author=1
    int newid = addImage(imgfile, origname, timezone, 1, pw, db);
    if (newid == 0) return 0;

    // now we create the necessary tag relationship
    try {
      PreparedStatement st = db.prepareStatement
        ("INSERT INTO imagetag (image, tag) VALUES (?, ?);");
      st.setInt(1, newid);
      st.setInt(2, tag);
      st.executeUpdate();
      st.close();
    } catch (SQLException e) {
      pw.println("*** database error: " + e.getMessage());
      // still return newid at this point, because the insert into
      // the main table(s) was done.  it's just lost to the tag system.
    }
    return newid;
  }

  /**
   * looks through a ZIP file for any recognized images and adds them
   * to the database.  Meant to be called through either addZip() or
   * addZipByTag() because there are two interfaces.  All this needs
   * is a parameter with a default value, but instead let's have some
   * clunky clunk java polymorphism!
   *
   * @param file should be a ZIP archive
   * @param owner user to receive the images
   * @param timezone String timezone id to interpret exif timestamps
   * @param out PrintWriter to send log to
   * @param db Connection you want to use
   * @return LinkedList<Integer> list of image IDs
   */

  @SuppressWarnings("unchecked")
  private static ArrayList<Integer> do_addZip
    (File file, int owner, int tag, String timezone,
     PrintWriter out, Connection db)
  {
    if (owner == 0 && tag == 0) {
      out.println("internal error: neither owner nor tag was set");
      return null;
    } else if (owner != 0 && tag != 0) {
      out.println("internal error: both owner and tag were set");
      return null;
    }

    int added = 0;
    ImageFilter filter = new ImageFilter();
    ArrayList<Integer> newids = new ArrayList<Integer>();

    try {
      ZipFile zf = new ZipFile(file);
      // NB you will get a clunky warning here for "unchecked cast"
      // but there is no obvious way around this inter-library type screwup.
      ArrayList<ZipEntry> zipentries =
        Collections.list((Enumeration<ZipEntry>)zf.entries());
      for (ZipEntry ze : zipentries) {
        String ename = ze.getName().toLowerCase();
        out.println(ename);
        // skip the nonsense AppleDouble crap that sometimes gets into
        // the zip file
        if (ename.startsWith("__macosx")) continue;
        // decide if this entry is a known image type
        if (filter.accept(ename)) {
          // what we really need here is just a temp file
          File tmpfile = PhotoUtils.getNewUploadFile(owner, ename);
          dumpStream(zf.getInputStream(ze), tmpfile);
          int newid;
          if (tag != 0) {
             newid = addImageByTag(tmpfile, ename, timezone, tag, out, db);
          } else {
             newid = addImage(tmpfile, ename, timezone, owner, out, db);
          }
          if (newid > 0) {
            ++added;
            newids.add(newid);
            out.print("  stored as imageid " + newid + "\n");
          } else {
            out.println("** Image insertion failed!");
          }
          if (!tmpfile.delete()) {
            out.print("Warning: couldn't delete temp file: ");
            out.println(tmpfile.toString());
          }	       
        } // if (zip entry was image)
      } // while (more zip entries)
    } catch (IOException e) {
      out.println("*** Error ***");
      out.println(e);
      out.println("Processing aborted.");
    }
    out.print("Finished processing ZIP file.  ");
    out.print(added);
    out.print(" images extracted.\n");

    return newids;
  }

  /**
   * in the old style, images have authors.
   */

  public static ArrayList<Integer> addZip
    (File file, int owner, String timezone, PrintWriter out, Connection db)
  {
    return do_addZip(file, owner, 0, timezone, out, db);
  }

  /**
   * in the new style, images have tags.
   */

  public static ArrayList<Integer> addZipByTag
    (File file, int tag, String timezone, PrintWriter out, Connection db)
  {
    return do_addZip(file, 0, tag, timezone, out, db);
  }

  /**
   * method to dump a given InputStream to a file.  Handy for extracting
   * entries from ZIP archive.  Closes the InputStream after dumping it.
   *
   * @param in InputStream to read
   * @param out File to write to
   * @return nothing
   * @exception IOException
   */

  private static void dumpStream(InputStream in, File out)
  throws IOException
  {
    // try to buffer to make this a little more efficient
    BufferedInputStream bin = new BufferedInputStream(in);

    BufferedOutputStream bout = 
      new BufferedOutputStream(new FileOutputStream(out));
    int b;
    while ((b = in.read()) != -1) bout.write(b);
    bout.close();
    bin.close();
    in.close();
    return;
  }


}




