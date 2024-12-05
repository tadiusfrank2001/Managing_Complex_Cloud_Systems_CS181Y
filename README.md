# Managing-Complex-Cloud-Systems-CS181Y

## Basic Overview
1) Participate in a 15-week practicum focused on building and maintaining the backend cloud services and human teams that ensure service uptime and monitoring.
2) Coordinate with a team of three engineers to build and monitor our cloud backend service.
3) Acquire the fundamentals of Cloud Engineering by utilzing AWS Cloud Services to construct a backend service and database to host our assigned website.
4) Master the fundamentals of Site-Reliability Engineering by setting up a paging (PagerDuty) and web monitoring system (New Relic) to track the uptime for our assigned website.
5) Scale up our backend cloud service using a relational database, additional copies of prod server, elastic file system, and a load balancer.
7) Provide 24/7 monitoring and maintenance of website due to constant traffic spikes and simulated DDos attack via bots, made 95% uptime each week for five weeks.

## Skills
+ Site Reliability & Monitoring (On-Call Experience)
+ AWS & Cloud Engineering
+ Relational Databases
+ Software Architecture
+ Strong Understanding of Complex Systems (Software, Buisness Organization, etc.)
+ Cross Team Collaboration 

## Technologies Used

### 1. Cloud Back-End and Hosting

###  1a. AWS Services

`Amazon EC2 (Elastic Compute Cloud)`: Provides scalable virtual servers to host your website's backend logic, applications, and databases.

`AWS Lambda`: Allows you to run backend code without provisioning servers. It's often used for creating serverless architectures, where you only pay for the compute time you consume.

`Amazon Relational Database Service (RDS)`: Provides managed relational databases like MySQL, PostgreSQL, and others.

`Amazon Elastic File System (EFS)`: scalable file storage service provided by AWS (Amazon Web Services), scales your storage capacity up or down as you add or remove files, without the need for manual intervention across mutliple instances.

`AWS Application Load Balancer (ALB)`: distributes incoming application traffic across multiple targets, such as Amazon EC2 instances, containers, and IP addresses.

### 1b. Servers
`Apache Tomcat`: open-source web server and servlet container developed by the Apache Software Foundation.

`Nginx`: high-performance web server and reverse proxy server. It's also used as a load balancer and HTTP cache. Mostly used for log information on the traffic via listening on our Port 443.

`Pkeep`: a  cloud-based service or platform that allows users to manageand maintain Python packages in a development environment.

### 1c. Operating System

`Ubuntu Linux`: a Linux distribution designed to be user-friendly and accessible. We needed an OS with consistent version realeases.

### 1d. Database
`PostgreSQL`: open-source relational database management system (RDBMS). Version 15.


### 1e. Languages

`JavaScript`: multi-paradigm language used to write front-end code for our website's UI in our `src` folder (log in page, image display page, image upload page).

`Java`: OOP language used to write the Java classes in our `src` folder for image proccessing (standard size, metadata collection for display, database addition, assign unique image ID) once uploaded through our website.

`Pyhton`: interpreted programming language used to set up our pkeep application to manage both the original image data and a caching layer to enhance performance via quick retrieval.

`MySql`: query language utilized to manage and store images and their metadata, images availaible in `art` folder.

`Bash`: command-line shell and scripting language utilized to navigate and interact with our Linux system. We also used it to edit the `Makefile` in the `war` to compile all of the java classes, build a .war file and deploy all the assets to a test and prod environment.


### 2. Site Monitoring Software

`PagerDuty`: an incident management and response platform designed to help organizations handle operational issues and ensure that systems and services remain available and performant. 

`New Relic`: a comprehensive observability platform that provides monitoring and performance management for applications, infrastructure, and digital experiences. It helps organizations gain insights into their systems, detect and troubleshoot issues, and optimize performance. 


## Project Timeline and Structure 

### Week 1 to 5:

In this milestone, we build the foundational backend service using AWS and our code base. Our flakeflickr site has serveral components a Postgres, file storage, a Java app, and a Python service process. Below I'll go into detail to explain the end-to-end process of storing, displaying, and retriving images on our site! The following diagrams are of our prod server!

#### 1. Transaction Flow for Image Display and Retrieval

<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Foundational%20AWS%20Backend.png" alt="Alt Text" width="500" />

1. A web browser makes a connection over HTTPS to the destination currently named in your DNS entry on port 443. It makes a HTTP GET request for a resource with a name such as `/img/1440/39998.jpg`.
2. The fakeflickr web app receives this request. The first thing it does is run some Postgres queries to verify that the authorization cookie is valid, and fetch the metadata for the requested image. The way fakeflickr connects to Postgres is defined in the `context.xml` file in `photo.war`.
3. The fakeflickr web app sends a packet to the pkeep process, which requests an image file be rendered. The instructions to pkeep are (a) the imageID (b) the desired size and (c) any modifications such a rotation or text overlay. This request is sent via a UDP packet to localhost, port 4770. This is hard-coded in the askPkeep function in `PhotoUtils.java`.
4. pkeep checks its pkeep_cache storage to see if this exact request has already been rendered. If so, it skips to step 6. If not, pkeep goes to pkeep_orig to find the original jpeg file for the given imageID.
5. pkeep processes the image data, and writes the finished product to its pkeep_cache directory.
6. pkeep sends a response back to the fakeflickr web app which contains the path to the finished image file. This path will be in pkeep_cache. The response is a UDP packet back to whatever port the request arrived from in step 3.
7. The fakeflickr web app reads the cache file from disk storage.
8. The fakeflickr web app sends the image data back to the browser as the response to the HTTPS request made in step 1.



#### 2. Transaction Flow for Uploading Images

<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Imageuploadtransctionflow.png" alt="Alt Text" width="500"/>


1. A web browser connects over HTTPS to port 443. It makes an HTTP POST request with a resource name such as `/rest/edit`.
2. The fakeflickr webapp checks the authorization cookie, then updates the Postgres database with the new data.
3. If there are new files to store, fakeflickr writes them to the pkeep_orig directory. Note that no other processing happens right now–pkeep will do it on demand if the image is ever displayed later.
4. fakeflickr responds to the HTTP POST request with a status code that is either success (200), or an error that indicates the request was invalid in some way (4xx), or an error that indicates the request was fine but the server failed for some reason (5xx).

#### Comments on Design:

+ This whole transaction flow takes about 10ms!
+ Some latency was added because we spinned our VM out of a US East (Ohio) location and not the US West (N. California) region! (We were in Claremont, CA for context)
+ At this stage, our Java App is directly facing the world on one server which could pose challenges on our systems reliability, availability, efficiency (Latency & Throughput).



### Week 5 to 10:

In this milestone, we setup Site Monitoring via PagerDuty and New Relic and assign 24/7 monitoring schedules for team. Here, we apply some of the core concepts of cybernectics (Control and Regulation, Information and Communication, Adaptation and Learning) to design a responsive and efficient incident response system to monitor our website and study the systems comprised of nonliving and living components.

<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Incident_Response_System.png" alt="Alt Text" width="500"/>

#### 1. Setting Up New Relic for Java (JVM w/ Tomcat)
1. Download the New Relic Java Agent from the New Relic Site.
2. Unzip the file and copy the `newrelic.jar` file to your application’s directory or a location accessible by your application. In our case, it's our Tomcat `lib`.
3. Configure the agent by opening the `newrelic.yml` file and overwrite the existing configuration with a copy generated from the New Relic website that includes your New Relic License Key.
4. Edit the `tomcat.conf` or the appropriate startup script for your Tomcat instance to include the New Relic agent. Find this line and edit the bash with the approriate filepath.
``` bash
    export JAVA_OPTS="$JAVA_OPTS -javaagent:/path/to/newrelic.jar"
```
5. Restart your Tomcat server to apply the changes!
6. Check the Tomcat system log using `journalctl -eu tomcat` to confirm that the New Relic agent is initializing. Look for a line similar to:
```vbnet

com.newrelic INFO: New Relic Agent v7.2.0 is initializing...

```
7. Set up some policies in New Relic (or use the default "golden signals") to track throughput, cpu usage, and web transaction percentages (latency).

#### 2. Pager Duty for 24/7 alerting

1. Create a Service in Pager Duty and reterive an integration key.
2. Log into New Relic and navigate to Alerts & AI to create a policy that tracks a number of processes (throughput, latency, CPU usage, web traffic, etc.)
3. Within that Alert Policy, naviagte to "Add a notification channel" > Choose PagerDuty > Add the integration key from step 1.
4. Save your alert policy configuration!
5. Test! Test! Test! You can simulate an alert in New Relic to ensure that it correctly triggers an incident in PagerDuty (ex. increase throughput by clicking through pages)


#### Comments on Design:

+ We used New Relic because it's free and came with some good perks like custom policy creation to track other aspects of our application!
+ Pager Duty allowed us to assign a 24/7 On-Call schedule to respond quickly to app outages or anomalies in our policies!
+ We made 98.9% availablilty over 6 weeks!

#### Challenges:

+ Abnormal web traffic surges ramp up our CPU usage and crash our Java App.
+ A memory leak was present due misconfirguration between the Java App and Postgres database in the `context.xml` file of `photo.war` in the `war` directory.
+ Our Availability is high but it required too much human supervision via paging!


### Week 10 to 15:

In this milestone, we scale up our backend system to handle 1000+ visitors and 10,000 requests per minute (RPM) for 15mins. Over the span of 15 minutes, the system will under load with 150,000 requests in total to similate a DDoS.

A DDoS attack means "Distributed Denial-of-Service (DDoS) Attack" and it is a cybercrime in which the attacker floods a server with internet traffic to prevent users from accessing connected online services and sites.

To achieve this, we horizontally scaled our previous system! This required four main changes to our current system:


<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Scaled%20Up%20AWS%20Backend.png" alt="Alt Text" width="500" />


#### 1. Create a App Server Pool for Application Load Balancer (ALP)

We needed to set up an app server pool of at least 3 instances, where each one has tomcat and fakeflickr installed. 

1. When setting up new app servers, think about any configuration changes that would be good to make before cloning the VM a bunch of times. Uninstall the `posrgresdl-server` server for instance since we are setting up an RDS database.
2. Snapshot the volume on your existing production VM. Volumes refers to persistaent storage attached to a VM such as the operating system, application files, databases(we deleted this!), and configuration data.
3. Convert the Volume into an Amazon Machine Image (AMI) to have a pre-configured template used to instatiate new instances.
4. Produce three to four instances of our modified prod server.

#### 2. Connect App Server Pool to Application Load Balancer (ALP) via a target group

After creating the app server pool, we set up an AWS Application Load Balancer (ALB) using the pool as a target group. An ALB distributes incoming application traffic across multiple targets, such as EC2 instances, containers, or IP addresses, to ensure that no single server is overwhelmed and to provide high availability, fault tolerance, and scalability for our application.

1. Utilize the AWS Management Console to create a target group of the registered instances from the previous step.
2. Utilize the AWS Management Console to create an ALB that listens on port 80 (incoming unecrypted HTTP web traffic) and has the target group of instances as the destination.


#### 2. Provisioning Network File System (NFS) with an Elastic File System (EFS)



#### 3. Configure and Populate an RDS database












