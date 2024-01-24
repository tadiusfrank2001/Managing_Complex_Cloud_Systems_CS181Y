# This Makefile can compile all of the java classes, build a .war file,
# and deploy all the assets to a test and prod environment.
#
# To build the Web ARchive (war) for Tomcat or other servlet container:
#
# + Check that you have GNU make: make --version
# + Set the SERVLET variable below to the jar with the servlet API,
#   which came with the Tomcat package
# + Run 'make war'
#
# The dev and prod recipes contain the instructions for how the war
# is deployed in my environment.  They may be useful as clues to how to
# deploy in your environment, but are unlikely to work as-is.
#
# This version only works with GNU make, and is much simpler than the BSD
# version that had to specify a new rule for each .class file.

DOC_PATH=war/javadoc
SRC_PATH=src/net/photoprism
DEST_PATH=war/WEB-INF/classes/net/photoprism

# The path to the servlet api jar will depend on the random decisions
# made by the person that prepared your tomcat package.
SERVLET=/usr/share/java/tomcat-servlet-4.0-api.jar

LIB=war/WEB-INF/lib
JUNIT=/usr/share/java/junit.jar:/usr/share/java/hamcrest-core.jar
CLASSPATH=$(SERVLET):$(JUNIT):$(LIB)/postgresql-42.2.9.jar:$(LIB)/cos.jar:$(LIB)/json-20200518.jar:war/WEB-INF/classes
JAVAC=javac -d war/WEB-INF/classes -classpath $(CLASSPATH) -deprecation -Xlint:unchecked
JAR=jar

PROD=ec2-user@fakeflickr.biz
PRODPATH=/opt/apache-tomcat-9.0.34
PRODCERT=/etc/letsencrypt/live/fakeflickr.biz

DEVCONF=/etc/tomcat9
DEVCERT=/etc/tomcat9/ssl

# --------------------------------------------------------
# Miscellaneous targets
# --------------------------------------------------------

all: base utils war clean

install:
	rm -rf /var/lib/tomcat9/webapps/ROOT/*
	cp -R war/* /var/lib/tomcat9/webapps/ROOT

clean::
	-rm *~ 2>/dev/null
	-rm $(SRC_PATH)/*~ 2>/dev/null

docs::
	javadoc -version -d $(DOC_PATH) $(SRC_PATH)/*.java

utils: ImageInserter ImageOrganizer ExifReader GPXInserter

war: servlets
	cd war; $(JAR) cf ../photo.war *

jar: servlets
	cd war/WEB-INF/classes; $(JAR) cf ../../../photo.jar *

updateversion::
	cat $(SRC_PATH)/PhotoUtils.java | sed -e "s/\"Id:[^\"]*\"/\"Id:`date`\"/" > PhotoUtils.new
	mv PhotoUtils.new $(SRC_PATH)/PhotoUtils.java

dev: war
	sudo -u tomcat9 cp photo.war /var/lib/tomcat9/webapps/ROOT.war
	cat conf/server.xml | sed s^CERTDIR^$(DEVCERT)^g \
          > $(DEVCONF)/server.xml
	cp conf/web.xml $(DEVCONF)
	cp conf/catalina.properties $(DEVCONF)
	chmod 640 $(DEVCONF)/{server.xml,web.xml,catalina.properties}
	sudo systemctl restart tomcat9
	grep -RH TODO war/js src/net/photoprism readme

# watch out for "permission denied" when tomcat tries to open ports
# 80/443.  This has been fixed by 'setcap cap_net_bind_service+ep
# /usr/lib/jvm/.../java', where the path was picked out of 'systemctl
# cat tomcat9'.  It will be lost when the java binary is overwritten.

prod: war
	scp photo.war conf/{server.xml,web.xml,catalina.properties} $(PROD):
	scp src/py/{pkeep_local.py,imgops.py,Dustismo.ttf} $(PROD):pkeep
	ssh $(PROD) \
          "sudo systemctl stop tomcat9 && \
          sudo -u tomcat cp ~/photo.war $(PRODPATH)/webapps/ROOT.war && \
          rm ~/photo.war && \
          sed s^CERTDIR^$(PRODCERT)^ server.xml \
          | sudo -u tomcat tee $(PRODPATH)/conf/server.xml >/dev/null && \
          rm server.xml && \
	  sudo -u tomcat cp web.xml $(PRODPATH)/conf && \
          rm web.xml && \
          sudo -u tomcat cp catalina.properties $(PRODPATH)/conf && \
          rm catalina.properties && \
          sudo systemctl start tomcat9"

backup::
	$(eval FILE=pg_dump.photo.`date +'%Y%m%d'`.xz)
	ssh $(HOST) "pg_dump -U photoprism photo" | xz > $(FILE)
	ls -lh $(FILE)

classpath:
	echo $(CLASSPATH)

# --------------------------------------------------------
# Programs
# --------------------------------------------------------

#DrawHistogram: $(DEST_PATH)/DrawHistogram.class
ExifReader: $(DEST_PATH)/ExifReader.class
FitsReader: $(DEST_PATH)/FitsReader.class
GPXInserter: $(DEST_PATH)/gps/GPXInserter.class
ImageInserter: $(DEST_PATH)/ImageInserter.class
ImageOrganizer: $(DEST_PATH)/ImageOrganizer.class
PhotoUtils: $(DEST_PATH)/PhotoUtils.class

# --------------------------------------------------------
# Base classes
# --------------------------------------------------------

base: dirs \
$(DEST_PATH)/TokenUtils.class \
$(DEST_PATH)/Photo.class \
$(DEST_PATH)/PhotoUtils.class \
$(DEST_PATH)/PhotoServlet.class \
$(DEST_PATH)/ExifReader.class

dirs:
	mkdir -p $(DEST_PATH)/gps

GPS_DEPS=$(DEST_PATH)/gps/GPXParser.class $(DEST_PATH)/gps/GPXTrackLog.class

# --------------------------------------------------------
# Servlet classes.  Need to make a special dependency rule if a class
# depends on other classes.  Otherwise it will accidentally work until
# it doesn't.
# --------------------------------------------------------

$(DEST_PATH)/ImageInserter.class: $(DEST_PATH)/ExifReader.class $(SRC_PATH)/ImageInserter.java
$(DEST_PATH)/gps/GPXInserter.class: $(GPS_DEPS) $(SRC_PATH)/gps/GPXInserter.java
$(DEST_PATH)/gps/GPXParser.class: $(DEST_PATH)/gps/GPXTrackLog.class $(SRC_PATH)/gps/GPXParser.java
#$(DEST_PATH)/HistogramServlet.class: $(SRC_PATH)/HistogramServlet.java $(DEST_PATH)/DrawHistogram.class
$(DEST_PATH)/LocationREST.class: $(DEST_PATH)/TokenServlet.class $(SRC_PATH)/LocationREST.java
$(DEST_PATH)/UploadREST.class: $(DEST_PATH)/ImageInserter.class $(SRC_PATH)/UploadREST.java
$(DEST_PATH)/BrowseREST.class: $(DEST_PATH)/SearchREST.class $(SRC_PATH)/BrowseREST.java
$(DEST_PATH)/EditREST.class: $(DEST_PATH)/TagREST.class $(SRC_PATH)/EditREST.java
$(DEST_PATH)/SuggestREST.class: $(DEST_PATH)/LocationREST.class $(SRC_PATH)/SuggestREST.java


# catchall rule for other .class files.  We assume they depend on
# PhotoUtils.class.
$(DEST_PATH)/%.class: $(SRC_PATH)/%.java
	$(JAVAC) $<

# note that order can accidentally matter here, if a later entry depends
# on an earlier entry!  Lol.
servlets: \
base \
$(DEST_PATH)/TokenServlet.class \
$(DEST_PATH)/BrowseREST.class \
$(DEST_PATH)/EditREST.class \
$(DEST_PATH)/ImageREST.class \
$(DEST_PATH)/PhotoUtils.class \
$(DEST_PATH)/SearchREST.class \
$(DEST_PATH)/LocationREST.class \
$(DEST_PATH)/UploadREST.class \
$(DEST_PATH)/TagREST.class \
$(DEST_PATH)/SuggestREST.class \
$(DEST_PATH)/RateLimiter.class
