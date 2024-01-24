package net.photoprism;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * executable class (has main()) that reorganizes all the known image files
 * according to the current suggestions made by getNewImageFile() in the
 * PhotoUtils class.  Can also update the size column if the -s command
 * line argument is given.  Basically this class runs a couple of hacks
 * that are needed if I change my mind in the middle of the game about how
 * to organize files etc.
 *
 * <p>Copyright (C) 2004 Michael A. Dickerson
 *
 * @author Michael A. Dickerson
 * @version 20040626
 */

public class ImageOrganizer
{
    /**
     * main() doesn't need any arguments; it takes everything it needs to
     * know from the database.
     *
     * @param args ignored
     * @return will exit nonzero on a fatal error
     */

    public static void main(String args[])
    {
	Connection  db = null;
	Statement   st = null;
	ResultSet   rs = null;
	PrintWriter pw = new PrintWriter(System.out, true);
	boolean  debug = false;

	// check command line argument; one of "-n" or "-y" is required.

	if (args.length < 1) { usage(); }
	if ("-n".equals(args[0])) {
	    debug = true;
	} else if ("-s".equals(args[0])) {
	    fixSizeColumn();
	    return;
	} else if (!"-y".equals(args[0])) {
	    usage();
	}

	try {
	    db = PhotoUtils.getConnection();
	    st = db.createStatement();
	    rs = st.executeQuery("SELECT imageid, imagefile " +
				 "FROM image ORDER BY imageid;");
	    
	    while (rs.next()) {
		int id = rs.getInt(1);
		File oldfile = new File(rs.getString(2));
		
		pw.print(id + ": ");

		if (!oldfile.exists()) {
		    pw.println("doesn't exist at " + oldfile);
		} else {
		    // find out where this image would be stored if it were
		    // created today, using today's getImageFile() call

		    File newfile = PhotoUtils.getImageFile(id);
		    if (newfile.getCanonicalPath().equals
			(oldfile.getCanonicalPath())) {
			pw.println("is already at " + newfile);
		    } else {
			pw.println("moves from: " + oldfile);
			pw.println("          to: " + newfile);
			if (!debug) {
			    try {
				// first try to move file--renameTo() doesn't
				// work for some reason
				PhotoUtils.copyFile(oldfile, newfile);
				if (!oldfile.delete()) {
				    throw new IOException("can't delete!");
				}
				// if we made it this far, update the table
				StringBuffer s = new StringBuffer();
				s.append("UPDATE image SET imagefile='");
				s.append(newfile.getCanonicalPath());
				s.append("' WHERE imageid = ");
				s.append(id);
				s.append(";");
				st.executeUpdate(s.toString());
			    } catch (FileNotFoundException e) {
				pw.println(oldfile + " does not exist!");
			    } catch (IOException e) {
				pw.println("copy error: " + e.toString());
			    } // note that a SQLException is not caught here
                              // and is therefore fatal; if the table can't be
			      // updated, it's probably better not to go
                              // plowing along moving files
			}
		    }
		}
	    }
	    
	} catch (ClassNotFoundException e) {
	    pw.println("unable to load driver "
		       + PhotoUtils.JDBC_DRIVER + ": " + e.toString());
	    System.exit(2);
	} catch (java.io.IOException e) {
	    pw.println("filesystem error: " + e.toString());
	    System.exit(3);
	} catch (SQLException e) {
	    pw.println("database error: " + e.getMessage());
	    System.exit(4);
	}

    } // public static void main()

    /**
     * method to run through the entire database and set the size column
     * to the size (bytes) of the file named in imageFile.  This should
     * not be needed unless something gets screwed up or (like now) you
     * have just invented the size column.
     */

    private static void fixSizeColumn()
    {
	Connection  db = null;
	Statement   st = null;
	ResultSet   rs = null;
	PrintWriter pw = new PrintWriter(System.out, true);

	try {
	    db = PhotoUtils.getConnection();
	    st = db.createStatement();
	    rs = st.executeQuery("SELECT imageid, imagefile " +
				 "FROM image ORDER BY imageid;");
	    
	    while (rs.next()) {
		int id = rs.getInt(1);
		pw.print(id + ": ");
		File f = new File(rs.getString(2));
		if (!f.exists()) {
		    pw.println("does not exist!");
		} else {
		    long size = f.length();
		    pw.println("setting size " + size);
		    StringBuffer s = new StringBuffer();
		    s.append("UPDATE image SET size = ");
		    s.append(size);
		    s.append(" WHERE imageid = ");
		    s.append(id);
		    s.append(';');
		    st.executeUpdate(s.toString());
		}
	    }

	    rs.close();

	} catch (ClassNotFoundException e) {
	    pw.println("unable to load driver "
		       + PhotoUtils.JDBC_DRIVER + ": " + e.toString());
	    System.exit(2);
	} catch(SQLException e) {
	    pw.println("database error: " + e);
	}

    }

    /**
     * method to print usage instructions and quit.  This method should only
     * ever be called from main() in ImageOrganizer, because it will call
     * System.exit(), which will probably kill off the entire JVM.
     */

    private static void usage()
    {
	System.err.println("ImageOrganizer will rearrange all of the raw " +
			   "image files stored\non the local hard disk. " +
			   "This might be a dangerous thing to do,\nso you " +
			   "have to read this message before you can run " +
			   "it.\n\n" +
			   "Usage: ImageOrganizer (-n | -y | -s)\n" +
			   "-n: print what would be done, but don't do it\n" +
			   "-y: print what would be done, and really do it\n" +
			   "-s: don't move anything, just fix size column");
	System.exit(1);
    }

} // class ImageOrganizer
