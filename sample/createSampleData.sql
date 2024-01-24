INSERT INTO tag (id, tag, description) VALUES (38, 'cs181sy', 'Starter data set');
INSERT INTO token (id, token, tag, refcode, expiration, level)
VALUES (1, 'pF6ikYqbT79m', 38, '32769660', '2022-01-01', 4);

\copy (
  select distinct l.*
  from location l, image i, imagetag it
  where l.locationid=i.location and i.imageid=it.image and it.tag=38
  union select * from location
  where locationid in (2, 3, 5, 24, 661, 135, 136, 305, 282, 283, 215, 233, 696)
  order by 1 asc
) to 'location_sample.csv' with csv;

\copy (
  select * from imagetag where tag=38
) to 'starter_imagetag.csv' with csv;

\copy (
  select i.* from image i, imagetag it
  where i.imageid = it.image and it.tag = 38
) to 'sample_image.csv' with csv;

\copy (
  select distinct p.*
  from person p, imagesubject ims, imagetag it
  where p.personid = ims.subject and ims.image = it.image and it.tag = 38
) to 'sample_person.csv' with csv;

\copy (
  select iv.* from imageviews iv, imagetag it
  where iv.image = it.image and it.tag = 38
) to 'sample_imageviews.csv' with csv;

\copy (
  select imagesubject.* from imagesubject
  inner join imagetag on imagesubject.image=imagetag.image
  where imagetag.tag = 38
) to 'sample_imagesubject.csv' with csv;

-- then you would copy them back in as follows:

\copy location from 'sample_location.csv' with csv;
\copy person from 'sample_person.csv' with csv;
\copy image from 'sample_image.csv' with csv;
\copy imagetag from 'sample_imagetag.csv' with csv;
\copy imagesubject from 'sample_imagesubject.csv' with csv;
