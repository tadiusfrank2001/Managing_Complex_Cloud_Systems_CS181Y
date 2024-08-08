### Managing-Complex-Cloud-Systems-CS181Y
+ A practicum course designed to build and maintain the backend cloud services that are utilized to support and power websites (Site-Relability).
+ Gradually Scale cloud system using a relational database, additional copy of production servers, elastic file system, and load balancer.
+ Provide 24/7 monitoring and maintenance of website due to constant traffic spikes via bots, made 95% uptime each week.

### Technology Used

# Cloud Back-End

Amazon EC2 (Elastic Compute Cloud): Provides scalable virtual servers to host your website's backend logic, applications, and databases.

AWS Lambda: Allows you to run backend code without provisioning servers. It's often used for creating serverless architectures, where you only pay for the compute time you consume.

Amazon S3 (Simple Storage Service): Used to store and serve static content like images, videos, and other media files.

Amazon RDS (Relational Database Service): Provides managed relational databases like MySQL, PostgreSQL, and others.

Apache Tomcat: open-source web server and servlet container developed by the Apache Software Foundation.

Pkeep: a tool used for managing and maintaining Python packages in a development environment.

PostgreSQL: open-source relational database management system (RDBMS).

Amazon Elastic File System (EFS): scalable file storage service provided by AWS (Amazon Web Services),  scales your storage capacity up or down as you add or remove files, without the need for manual intervention.

AWS Load Balancer: distributes incoming application traffic across multiple targets, such as Amazon EC2 instances, containers, and IP addresses. Mostly utilized for scaling up.

Nginx: high-performance web server and reverse proxy server. It's also used as a load balancer and HTTP cache. Mostly used for log information on the traffic via listening on our Port 443.

# Site Monitoring

PagerDuty: an incident management and response platform designed to help organizations handle operational issues and ensure that systems and services remain available and performant. 

New Relic: a comprehensive observability platform that provides monitoring and performance management for applications, infrastructure, and digital experiences. It helps organizations gain insights into their systems, detect and troubleshoot issues, and optimize performance. 












