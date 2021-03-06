/*
 Copyright 2015 Red Hat, Inc. and/or its affiliates.

 This file is part of lightblue.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.redhat.lightblue.mongo.test;

import static org.junit.Assert.assertNotNull;

import java.net.UnknownHostException;

import org.junit.ClassRule;
import org.junit.Test;

import com.mongodb.MongoClient;

public class MongoServerExternalResourceTest {

    @ClassRule
    public static final MongoServerExternalResource mongoServer = new MongoServerExternalResource();

    @Test
    public void testGetConnection() throws UnknownHostException {
        MongoClient client = mongoServer.getConnection();

        assertNotNull(client);
        assertNotNull(client.getDB("test"));
    }
}
