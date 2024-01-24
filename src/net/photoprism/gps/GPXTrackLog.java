package net.photoprism.gps;

import java.util.ArrayList;
import java.util.Collections;

/**
 * A class to model the simplified subset of possible data in a GPX
 * log that we are interested in.  We accept one list of trackpoints,
 * one list of waypoints, and can map arbitrary points in time into
 * either of these lists.
 *
 * NB: Compiling with javac 1.4 requires the option "-source 1.4" to
 * allow the use of assert.
 *
 * @author mikey@singingtree.com (Mikey Dickerson)
 * @version 20110709
 */

public class GPXTrackLog
{

	/**
	 * A class that represents a point in 3 dimensional space, where
	 * the 4 dimensions are: latitude, longitude, altitude, time.
	 */

	public static class Timepoint implements Comparable<Timepoint>
	{
		public Timepoint(double lat, double lon)
		{
			this.lon = lon;
			this.lat = lat;
		}

		public Timepoint(long t)
		{
			this.time = t;
		}

		/**
		 * Timepoints can be sorted and searched by timestamp.
		 */

		public int compareTo(Timepoint other) {
			// What a mess.
			return new Long(time).compareTo
			(new Long(other.time));
		}

		public String toString() {
			return this.time + ":(" + this.lat + ", " + this.lon + ")";
		}

		public double lon;
		public double lat;
		public double ele;
		public long time;
	}

	public GPXTrackLog()
	{
		this.trackpoints = new ArrayList<Timepoint>();
		this.waypoints = new ArrayList<Timepoint>();
		sorted = true;
	}

	public void addTrackpoint(Timepoint p)
	{
		this.trackpoints.add(p);
		sorted = false;
	}

	public void addWaypoint(Timepoint p)
	{
		this.waypoints.add(p);
		sorted = false;
	}

	public ArrayList<Timepoint> getTrackpoints()
	{
		sortLists();
		return this.trackpoints;
	}

	public ArrayList<Timepoint> getWaypoints()
	{
		sortLists();
		return this.waypoints;
	}

	public int countTrackpoints() { return trackpoints.size(); }
	public int countWaypoints() { return waypoints.size(); }

	/**
	 * Find position at time t.  We search the trackpoint list for the nearest
	 * points and apply a linear interpolation between them.  null is returned
	 * unless an actual logged point is within a given search radius.  Otherwise
	 * a gpx file containing '2010-01-01' and '2011-01-01' will "match" any
	 * t in 2010, with probably trash output.
	 * 
	 * @param t time in millis
	 * @param tolerance return null if we don't have any logged points within
	 * at most this many ms.
	 * @return a Timepoint, or null
	 */

	public Timepoint timeToTrackpoint(long t, long tolerance)
	{
		this.sortLists();
		int i = Collections.binarySearch(this.trackpoints, new Timepoint(t));
		if (i > 0) return this.trackpoints.get(i); // exact match, unlikely
		int insertionPoint = -(i+1);
		if (insertionPoint == 0) {
		  // t is off the beginning of the log.
		  if (this.trackpoints.get(0).time - t < tolerance) {
		    return this.trackpoints.get(0);
		  } else {
		    return null;
		  }
		} else if (insertionPoint == this.trackpoints.size()) {
		  // t is off the end of the log.
		  if (t - this.trackpoints.get(insertionPoint - 1).time < tolerance) {
		    return this.trackpoints.get(insertionPoint - 1);
		  } else{
		    return null;
		  }
		}
    // now we know we have 3 time points ordered as follows:
    // points[insertionPoint-1] < t < points[insertionPoint]
		Timepoint before = this.trackpoints.get(insertionPoint - 1);
		Timepoint after = this.trackpoints.get(insertionPoint);
		// require at least one of the logged points to be within search radius.
		if (t - before.time > tolerance && after.time - t > tolerance) {
		  return null;
		}
		// calculate how far along the (before -> after) line t falls
		double dist = (double)(t - before.time) / (double)(after.time - before.time);
		assert (0 < dist && dist < 1.0);
		Timepoint interpolated = new Timepoint(t);
		interpolated.lat = before.lat + (after.lat - before.lat) * dist;
		interpolated.lon = before.lon + (after.lon - before.lon) * dist; 
		return interpolated;
	}

	/**
	 * Find a waypoint close enough to time t.  Null if no waypoint is found
	 * within max specified distance.  Also null if more than one waypoint
	 * exists within search radius, because waypoints are expected to be sparse.
	 *
	 * @param t time in millis
	 * @param tolerance maximum distance of waypoint from t
	 * @return a Timepoint, or null
	 */

	public Timepoint timeToWaypoint(long t, long tolerance)
	{
		this.sortLists();
    int i = Collections.binarySearch(this.waypoints, new Timepoint(t));
    int nearest;
    if (i > 0) {
      // exact match is unlikely except in contrived situations
      nearest = i;
    } else {
      int insertionPoint = -(i+1);
      if (insertionPoint == 0) {
        // t is off the beginning of log.
        nearest = 0;
      } else if (insertionPoint == this.waypoints.size()) {
        // t is off the end of the log.
        nearest = insertionPoint - 1;
      } else {       
        // we must have 3 time points ordered as follows:
        // points[insertionPoint-1] < t < points[insertionPoint]
        // waypoint search does not interpolate, just rounds to the nearer
        // of the two.
        Timepoint before = this.waypoints.get(insertionPoint - 1);
        Timepoint after = this.waypoints.get(insertionPoint);
        if (t - before.time < after.time - t) {
          nearest = insertionPoint - 1;
        } else {
          nearest = insertionPoint;
        }
      }
    }

		if (nearest > 0 && t - this.waypoints.get(nearest-1).time < tolerance) {
		  // the previous waypoint is too close
		  return null;
		} else if (nearest < this.waypoints.size() - 1 && 
		    this.waypoints.get(nearest+1).time - t < tolerance) {
		  // the next waypoint is too close
		  return null;
		} else if (Math.abs(waypoints.get(nearest).time - t) > tolerance) {
		  // nearest is too far away
		  return null;
		}
		return waypoints.get(nearest);
	}
		
	/**
	 * First timepoint in track log.  Waypoints often have t=0, so are
	 * ignored.
	 * 
	 * @return Timepoint first in log.
	 */
	
	public Timepoint getFirst()
	{
		sortLists();
		return this.trackpoints.get(0);
	}

	/**
	 * Last timepoint in track log.  Waypoints often have t=0, so are ignored.
	 * @return Timepoint last in log.
	 */
	
	public Timepoint getLast()
	{
		sortLists();
		return this.trackpoints.get(this.trackpoints.size() - 1);
	}

	
	/**
	 * Dump list of points as a json object handy for plotting on the Google
	 * maps API.  See Polyline examples at:
	 * 
	 * http://code.google.com/apis/maps/documentation/javascript/overlays.html#Polylines
	 * 
	 * @return a possibly very long String.
	 */
	
	public String getGoogleMapsJson()
	{
	  StringBuffer s = new StringBuffer();
	  sortLists();
	  s.append('[');
	  for (Timepoint p: this.trackpoints) {
	    s.append("new google.maps.LatLng(");
	    s.append(p.lat);
	    s.append(',');
	    s.append(p.lon);
	    s.append("),\n");
	  }
	  s.append(']');
	  return s.toString();
	}
	
	private void sortLists()
	{
		if (sorted == true) return;
		Collections.sort(this.trackpoints);
		Collections.sort(this.waypoints);
		sorted = true;
	}

	protected ArrayList<Timepoint> trackpoints;
	protected ArrayList<Timepoint> waypoints;
	private boolean sorted;

	/**
	 * Invoke on its own for self test.  Don't forget the -ea argument to
	 * enable assertions, or you won't be testing anything.
	 */

	public static void main(String[] argv)
	{
		GPXTrackLog log = new GPXTrackLog();
		Timepoint tp;
		assert log.countTrackpoints() == 0;
		assert log.countWaypoints() == 0;

		// Test on a highly improbable route that has us traveling from
		// 1000:(1,1) to 2000:(2,2) to 3000:(3,3)...
		for (int i = 1; i < 11; ++i) {
			tp = new Timepoint(i, i);
			tp.time = i * 1000;
			log.addTrackpoint(tp);
		}

		// Create two waypoints at 7500:(8,8) and 7100:(7,7)
		tp = new Timepoint(7, 7);
		tp.time = 7100;
		log.addWaypoint(tp);
		tp = new Timepoint(8, 8);
		tp.time = 7500;
		log.addWaypoint(tp);

		assert log.countTrackpoints() == 10;
		assert log.countWaypoints() == 2;

		assert log.getFirst().time == 1000;
		assert log.getLast().time == 10000;

		// Look up a trackpoint at t=5000
		tp = log.timeToTrackpoint(5000, 200);
		System.out.println("timeToTrackpoint(5000, 200) => " + tp);
		assert (tp.lat == 5 && tp.lon == 5);

		// Look up a trackpoint between t=4000 and t=5000, get an
		// interpolated point between the two.
		tp = log.timeToTrackpoint(4900, 200);
		System.out.println("timeToTrackpoint(4900, 200) => " + tp);
		assert tp.lat == 4.9 && tp.lon == 4.9;
		
		// Look up a trackpoint almost at the end of the log, get an
		// interpolated point.
		tp = log.timeToTrackpoint(9900, 200);
		System.out.println("timeToTrackpoint(9900, 200) => " + tp);
		assert tp.lat == 9.9 && tp.lon == 9.9;
		
		// Look up a point between two logged points, but too far from
		// either, and fail.
		tp = log.timeToTrackpoint(4500, 200);
		System.out.println("timeToTrackpoint(4500, 200) => " + tp);
		assert tp == null;
		
		// Look up a trackpoint near the beginning of the log
		tp = log.timeToTrackpoint(900, 200);
		System.out.println("timeToTrackpoint(900, 200) => " + tp);
		assert tp.lat == 1 && tp.lon == 1;
		
		// Look up a trackpoint out of range, and fail.
		tp = log.timeToTrackpoint(100, 200);
		System.out.println("timeToTrackpoint(100, 200) => " + tp);
		assert tp == null;
		
		// Look up a trackpoint off the end of the log
		tp = log.timeToTrackpoint(10100, 200);
		System.out.println("timeToTrackpoint(10100, 200) => " + tp);
		assert tp.lat == 10 && tp.lon == 10;
		
		// Look up a waypoint around t=5000 (and fail)
		tp = log.timeToWaypoint(5000, 200);
    System.out.println("timeToWaypoint(5000, 200) => " + tp);
		assert tp == null;

		// Look up a waypoint around time 7 (and fail, range too wide)
		tp = log.timeToWaypoint(7000, 1000);
		System.out.println("timeToWaypoint(7000, 2000) => " + tp);
		assert(tp == null);

		// Look up a waypoint around time 7 (and succeed)
		tp = log.timeToWaypoint(7000, 200);
		System.out.println("timeToWaypoint(7000, 1000) => " + tp);
		assert tp.lat == 7 && tp.lon == 7;

		System.out.println("in json form for google maps:");
		System.out.println(log.getGoogleMapsJson());
		System.out.println("all tests passed.");

	}
}


