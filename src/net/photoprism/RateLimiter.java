package net.photoprism;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantLock;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;

/**
 * A filter that implements some simple rate limits.  It is only meant
 * to protect against cases like brute-forcing the token resolving
 * API, so it only reacts to failed requests (HTTP response >= 400).
 *
 * An in-memory table is checked on each request that arrives at the
 * filter.  If the client IP address has failed more than
 * CLIENT_FAIL_LIMIT times in the last WINDOW_LENGTH_SECS, then the
 * request is returned with an HTTP 429 and not further processed.
 *
 * At the same time, if the global total count of failed requests is
 * more than GLOBAL_FAIL_LIMIT in the last WINDOW_LENGTH_SECS, then
 * all requests from all clients are returned with HTTP 429.  This is
 * meant to make it impractical to search the token space even with an
 * arbitrarily large botnet.
 *
 * Beware that the global circuit breaker is only checked when global
 * garbage collection is done, which is once every GC_INTERVAL_SECS.
 * That means that for up to GC_INTERVAL_SECS, you can run a
 * distributed attack where each client gets CLIENT_FAIL_LIMIT
 * tries before it is locked out.  This seems like not a major risk.
 *
 * Requests that are rejected with HTTP 429 are not logged or counted
 * toward the failure limit.  This should make the server slightly
 * harder to crash by running it out of memory.
 *
 * No attempt is made to persist the state across server restarts.
 *
 * @author Mikey Dickerson
 * @date 20200329
 */

public class RateLimiter extends HttpFilter
{

   private static final int WINDOW_LENGTH_SECS = 3600;
   private static final int CLIENT_FAIL_LIMIT = 10;
   private static final int GLOBAL_FAIL_LIMIT = 1000;
   private static final int GC_INTERVAL_SECS = 30;

   private Hashtable<String, ClientProfile> clients;
   private long ddosLockoutUntil;
   private long lastGarbageCollect;
   private ReentrantLock gcLock;
   private ServletContext servletContext;
   private boolean debug;

   private class LoggedRequest
   {
      public long ts;
      public int httpStatus;

      public LoggedRequest(long t, int h) {
         this.ts = t;
         this.httpStatus = h;
      }
   }

   private class ClientProfile
   {
      public String remoteAddr;
      public ArrayDeque<LoggedRequest> requests;
      public long lockedUntil;
      public int failCount;
      public long oldestFail;

      public ClientProfile(String addr) {
         this.remoteAddr = addr;
         this.requests = new ArrayDeque<LoggedRequest>();
         this.lockedUntil = 0;
         this.failCount= 0;
         this.oldestFail = 0;
      }

      public synchronized void check(long now) {
         // first expire any events older than WINDOW_LENGTH_SECS
         long cutoff = now - WINDOW_LENGTH_SECS * 1000;
         while (!requests.isEmpty() &&
                requests.getFirst().ts < cutoff) {
            requests.removeFirst();
         }

         // now see if more than CLIENT_FAIL_LIMIT failures remain
         failCount = 0;
         oldestFail = 0;
         for (LoggedRequest r : requests) {
            if (r.httpStatus >= 400) {
               failCount += 1;
               if (r.ts < oldestFail || oldestFail == 0)
                  oldestFail = r.ts;
            }
         }
         if (failCount > CLIENT_FAIL_LIMIT && lockedUntil == 0) {
            lockedUntil = oldestFail + WINDOW_LENGTH_SECS * 1000;
            log("brute force lockout triggered for "
                + remoteAddr + ", releases in "
                + (lockedUntil - now) / 1000 + "s");
         }
         if (debug) {
            log("checked table for client " + remoteAddr +
                " failCount=" + failCount +
                " oldestFail=" + oldestFail +
                " lockedUntil=" + lockedUntil);
         }
      }  
   }

   public void init(FilterConfig config) throws ServletException
   {
      ddosLockoutUntil = 0;
      lastGarbageCollect = 0;
      gcLock = new ReentrantLock();
      clients = new Hashtable<String, ClientProfile>();
      servletContext = config.getServletContext();
      debug = (config.getInitParameter("debug") != null);
      if (debug) log("debug flag set for " + config.getFilterName());
   }

   public void destroy()
   {
      this.clients.clear();
      this.clients = null;
   }

   public void doFilter
   (HttpServletRequest req, HttpServletResponse res, FilterChain chain)
   throws IOException, ServletException
   {
      long now = System.currentTimeMillis();

      if (now < ddosLockoutUntil) {
         // the global circuit breaker is tripped.  The request is
         // dropped and nothing more happens until the timeout expires.
         res.setHeader("Retry-After",
                       "" + (this.ddosLockoutUntil - now) / 1000);
         res.setStatus(429);
         return;
      }

      // Check and see if global table maintenance is due.  There are
      // typically dozens of threads that get here at the same time,
      // so the idea with gcLock is to make only one of them stop and
      // do maintenance, and let the others blow past it.
      if (now > lastGarbageCollect + GC_INTERVAL_SECS * 1000 &&
          gcLock.tryLock()) {
         try {
            lastGarbageCollect = now;
            int globalFails = 0;
            long oldestFail = now;
            for (ClientProfile cp : clients.values()) {
               cp.check(now);
               globalFails += cp.failCount;
               if (cp.oldestFail < oldestFail) oldestFail = cp.oldestFail;
            }
            if (globalFails > GLOBAL_FAIL_LIMIT) {
               ddosLockoutUntil = oldestFail + (WINDOW_LENGTH_SECS * 1000);
               log("global brute force protection triggered, " +
                   "releases in " + (ddosLockoutUntil - now) / 1000 + "s");
            }
            if (debug) {
               log("ran global maintenance:" +
                   " globalFails=" + globalFails +
                   " oldestFail=" + oldestFail +
                   " clients.size()=" + clients.size() +
                   " time=" + (System.currentTimeMillis() - now) + "ms");
            }
         } finally {
            gcLock.unlock();
         }
      }

      String addr = req.getRemoteAddr();
      if (!clients.containsKey(addr)) {
         clients.put(addr, new ClientProfile(addr));
      }
      ClientProfile cp = clients.get(addr);
      cp.check(now);
      if (cp.lockedUntil > now) {
         // the per-client failure limit is exceeded.  The request is
         // dropped.
         res.setHeader("Retry-After", "" + (now - cp.lockedUntil) / 1000);
         res.setStatus(429);
      } else {
         // process the request and store the return code.  We only
         // store failures because successes aren't used for
         // anything, but this could change.
         chain.doFilter(req, res);
         if (res.getStatus() >= 400) {
            cp.requests.addLast(new LoggedRequest(now, res.getStatus()));
         }
      }
   }

   private void log(String msg) { servletContext.log(msg); }
}
