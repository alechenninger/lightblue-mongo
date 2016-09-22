/*
 Copyright 2013 Red Hat, Inc. and/or its affiliates.

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
package com.redhat.lightblue.mongo.crud;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.ReadPreference;
import com.mongodb.DBCursor;

import com.redhat.lightblue.interceptor.InterceptPoint;
import com.redhat.lightblue.crud.CRUDOperationContext;
import com.redhat.lightblue.crud.CRUDOperation;
import com.redhat.lightblue.crud.DocCtx;

import com.redhat.lightblue.util.JsonDoc;
import com.redhat.lightblue.util.Error;
import java.util.concurrent.TimeUnit;

/**
 * Basic doc search operation
 */
public class BasicDocFinder implements DocFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicDocFinder.class);
    private static final Logger RESULTSET_LOGGER = LoggerFactory.getLogger("com.redhat.lightblue.crud.mongo.slowresults");

    private final Translator translator;
    private ReadPreference readPreference;
    private int maxResultSetSize = 0;
    private long maxQueryTimeMS = 0;

    public BasicDocFinder(Translator translator, ReadPreference readPreference) {
        this.translator = translator;
        this.readPreference = readPreference;
    }

    @Override
    public void setMaxResultSetSize(int size) {
        maxResultSetSize = size;
    }

    @Override
    public void setMaxQueryTimeMS(long maxQueryTimeMS) {
        this.maxQueryTimeMS = maxQueryTimeMS;
    }

    @Override
    public long find(CRUDOperationContext ctx,
                     DBCollection coll,
                     DBObject mongoQuery,
                     DBObject mongoProjection,
                     DBObject mongoSort,
                     Long from,
                     Long to) {
        LOGGER.debug("Submitting query {}", mongoQuery);

        long executionTime = System.currentTimeMillis();
        DBCursor cursor = null;
        try {
            cursor = coll.find(mongoQuery, mongoProjection);
            if (readPreference != null) {
                cursor.setReadPreference(readPreference);
            }

            if (maxQueryTimeMS > 0) {
                cursor.maxTime(maxQueryTimeMS, TimeUnit.MILLISECONDS);
            }

            executionTime = System.currentTimeMillis() - executionTime;

            LOGGER.debug("Query evaluated");
            if (mongoSort != null) {
                cursor = cursor.sort(mongoSort);
                LOGGER.debug("Result set sorted");
            }
            int numMatched = cursor.count();
            int nRetrieve=numMatched;
            List<DBObject> mongoResults=null;            
            List<JsonDoc> jsonDocs=null;

           LOGGER.debug("Applying limits: {} - {}", from, to);
            long retrievalTime=0;

            // f and t are from and to indexes, both inclusive
            int f=from==null?0:from.intValue();
            int t=to==null?numMatched-1:to.intValue();
            if(f<0)
                f=0;
            if(t>=numMatched)
                t=numMatched-1;
            if(t<f||f>=numMatched)
                nRetrieve=0;
            
            if(nRetrieve>0) {
                nRetrieve=t-f+1;                
                cursor.skip(f);
                cursor.limit(nRetrieve);
                if (maxResultSetSize > 0 && nRetrieve > maxResultSetSize) {
                    LOGGER.warn("Too many results:{} of {}", nRetrieve, numMatched);
                    RESULTSET_LOGGER.debug("resultset_size={}, requested={}, query={}", numMatched, nRetrieve, mongoQuery);
                    throw Error.get(MongoCrudConstants.ERR_TOO_MANY_RESULTS, Integer.toString(nRetrieve));
                }
                
                LOGGER.debug("Retrieving results");
                retrievalTime = System.currentTimeMillis();
                mongoResults = cursor.toArray();
                retrievalTime = System.currentTimeMillis() - retrievalTime;
                
                LOGGER.debug("Retrieved {} results", mongoResults.size());
                jsonDocs = translator.toJson(mongoResults);
                if(jsonDocs.size()!=nRetrieve)
                    throw Error.get(MongoCrudConstants.ERR_MONGO_RESULTSET_MISMATCH,"Requested="+nRetrieve+" Retrieved="+jsonDocs.size());
                
                ctx.addDocuments(jsonDocs);
                for (DocCtx doc : ctx.getDocuments()) {
                    doc.setCRUDOperationPerformed(CRUDOperation.FIND);
                    ctx.getFactory().getInterceptors().callInterceptors(InterceptPoint.POST_CRUD_FIND_DOC, ctx, doc);
                }
                LOGGER.debug("Translated DBObjects to json");
            }
            if (RESULTSET_LOGGER.isDebugEnabled() && (executionTime > 100 || retrievalTime > 100)) {
                RESULTSET_LOGGER.debug("execution_time={}, retrieval_time={}, resultset_size={}, data_size={}, query={}, from={}, to={}",
                                       executionTime, retrievalTime, mongoResults==null?0:mongoResults.size(),
                                       jsonDocs==null?0:Translator.size(jsonDocs),
                                       mongoQuery,
                                       f, t);
            }            
            return numMatched;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

}
