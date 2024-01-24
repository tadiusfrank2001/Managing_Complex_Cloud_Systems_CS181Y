#!/usr/bin/python

import boto
import S3

PUB_KEY = AWS_ACCESS_KEY_ID = '1C3E8KXW99S8N6P4RZ02'
SECRET_KEY = AWS_SECRET_ACCESS_KEY = 'cPlxJPq1j7N0aAyqZM/PnEc+BhTOXMnQ9H9ayxGs'

def connect_s3():
    return boto.connect_s3(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)

def GetConn(is_secure=False):
    return S3.AWSAuthConnection(AWS_ACCESS_KEY_ID,
                                AWS_SECRET_ACCESS_KEY,
                                is_secure=is_secure)

def GetUrlGenerator(is_secure=False):
    return S3.QueryStringAuthGenerator(AWS_ACCESS_KEY_ID,
                                       AWS_SECRET_ACCESS_KEY,
                                       is_secure=is_secure)
