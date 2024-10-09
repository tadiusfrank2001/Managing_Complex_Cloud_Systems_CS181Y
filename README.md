# Managing-Complex-Cloud-Systems-CS181Y

## Basic Overview
1) Participate in a 15-week practicum focused on building and maintaining the backend cloud services and human teams that ensure service uptime and monitoring.
2) Coordinate with a team of three engineers to build and monitor our cloud backend service.
3) Acquire the fundamentals of Cloud Engineering by utilzing AWS Cloud Services to construct a backend service and database to host our assigned website.
4) Master the fundamentals of Site-Reliability Engineering by setting up a paging (PagerDuty) and web monitoring system (New Relic) to track the uptime for our assigned website.
5) Scale up our backend cloud service using a relational database, additional copies of production servers, elastic file system, and load balancer.
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

`Amazon S3 (Simple Storage Service)`: Used to store and serve static content like images, videos, and other media files.

`Amazon RDS (Relational Database Service)`: Provides managed relational databases like MySQL, PostgreSQL, and others.

`Amazon Elastic File System (EFS)`: scalable file storage service provided by AWS (Amazon Web Services), scales your storage capacity up or down as you add or remove files, without the need for manual intervention across mutliple instances.

`AWS Load Balancer`: distributes incoming application traffic across multiple targets, such as Amazon EC2 instances, containers, and IP addresses. Mostly utilized for scaling up.

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

**Week 1 to 5:** Build the Foundational AWS Backend using AWS Services and our code base. Below is diagram of our production server!

<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Foundational%20AWS%20Backend.png" alt="Alt Text" width="500" />


**Week 5 to 10:** Setup Site Monitoring via PagerDuty and New Relic and assign 24/7 monitoring schedules for team. Our team scored a 95% uptime score!

**Week 10 to 15:** Scale up our Backend system to handle 1000 visitors simultaneously and a simulated DDos attack via bots. Below is a diagram of scaled up system!

<img src="https://github.com/tadiusfrank2001/Managing_Complex_Cloud_Systems_CS181Y/blob/main/Scaled%20Up%20AWS%20Backend.png" alt="Alt Text" width="500" />










