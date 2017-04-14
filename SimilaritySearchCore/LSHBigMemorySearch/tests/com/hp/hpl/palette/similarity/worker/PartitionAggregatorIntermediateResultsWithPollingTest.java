/**
“© Copyright 2017  Hewlett Packard Enterprise Development LP
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.”
*/
package com.hp.hpl.palette.similarity.worker;

import com.hp.hpl.palette.similarity.datamodel.TimeSeriesSearch;

import org.zeromq.ZMQ;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.List;

public class PartitionAggregatorIntermediateResultsWithPollingTest  {
   
	  private static final Log LOG = LogFactory.getLog(PartitionAggregatorIntermediateResultsWithPollingTest.class.getName());
	
	  public static class SimulatedPartitioner extends Thread {
		 private  ZMQ.Context context; 
		 private int processId; 
		 private  int sleepTime; 
		 
		 public SimulatedPartitioner (ZMQ.Context context, int processId) {
			this.context = context;
			this.sleepTime = sleepTime;
			this.processId = processId; 
		 }
		 
		 public void run() {
	   	    	
			    //(String querySearchId, int associatedRvalue, int partitionId, int threadProcessorId)
			    String querySearchId =  UUID.randomUUID().toString();
			    int associatedRvalue = 3; 
			    int partitionId = 1000;
			    int threadProcessorId = 77; 
			    TimeSeriesSearch.IntermediateQuerySearchResult intermediateResult =
			    		new TimeSeriesSearch.IntermediateQuerySearchResult(querySearchId, associatedRvalue, partitionId, threadProcessorId);
			    int numberOfSearchResult = 5;
			    for (int i =0; i<numberOfSearchResult; i++) {
			    	TimeSeriesSearch.SearchResult sr = new TimeSeriesSearch.SearchResult();
			    	sr.id=100;
			    	sr.offset=1000;
			    	sr.distance=10000;
			    	intermediateResult.addSearchResult(sr);
			    }
			 
	   	    	
	   	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
	   	    	DataOutputStream dataOut = new DataOutputStream (out);
	   	    	try {
	   	    		intermediateResult.write(dataOut);
	   	    	  dataOut.close();
	   	    	}
	   	    	catch(IOException ex) {
	   	    		LOG.error ("fails to serialize the partition's intermediate result information", ex);
	   	    	}
	   	    
	   	    	
	   	    	byte[] message = out.toByteArray();
	   	        //  Socket to send messages on
	   	        ZMQ.Socket sink = context.socket(ZMQ.PUSH);
	   	        sink.connect("tcp://localhost:5558");
	   	        
	   	        //now to do the send.the last flag is "no more" to send. 
	   	        sink.send(message, 0, message.length, 0); 
	   	        
	   	    	LOG.info("the intermediate result information has been pushed out by the paritioner.. in the thread of: .." + 
	   	    	                     Thread.currentThread().getId());
	   	        
	   	    }
	 }
	
	  public static class SimulatedAggregator extends Thread {
			 private  ZMQ.Context context; 
			 
			 
			 public SimulatedAggregator (ZMQ.Context context ) {
				this.context = context;
				 
			 }
			 
			 public void run() {
		   	    	
				    //  Socket to send messages on
			        ZMQ.Socket sink = context.socket(ZMQ.PULL);
			        sink.bind("tcp://*:5558");

			        //  Initialize poll set
			        ZMQ.Poller items = new ZMQ.Poller (1);
			        items.register(sink, ZMQ.Poller.POLLIN);
			         

			        //  Process messages from both sockets
			        while (!Thread.currentThread ().isInterrupted ()) {
			            byte[] message;
			            items.poll();
			            if (items.pollin(0)) {
			               message = sink.recv(0);
			               process(message);
			            }
			             
			        }
			        sink.close ();
			  
		   	   }
			 
			  private void process(byte[] receivedBytes) {
				   
				    TimeSeriesSearch.IntermediateQuerySearchResult intermediateResult   =
						                      new TimeSeriesSearch.IntermediateQuerySearchResult();
			        //we are using polling here. 
		   	        try {
		   	            
			   	        ByteArrayInputStream in = new ByteArrayInputStream(receivedBytes);
			   	        DataInputStream dataIn = new DataInputStream(in);
			   	        
			   	        intermediateResult.readFields(dataIn);
			   	        dataIn.close();
		   	        
		   	        }
		   	        catch (Exception ex) {
		   	        	LOG.error("fails to receive/reconstruct the intermediate result information", ex);
		   	        }
		   	        
		   	        
		   	    	LOG.info("the runtime information received from the paritioner is the following for thread:...." 
		   	    	                                                               + Thread.currentThread().getId());
		   	     
		   	    	
		   	    	LOG.info("query id: " + intermediateResult.getQuerySearchId());
		   	    	LOG.info("partition id: " + intermediateResult.getPartitionId());
		   	    	LOG.info("thread processor id: " + intermediateResult.getThreadProcessorId());
		   	    	LOG.info("associated R value: " + intermediateResult.getAssociatedRvalue());
		   	    	LOG.info("number of search results is: " + intermediateResult.size());
		   	    	List<TimeSeriesSearch.SearchResult> results = intermediateResult.getSearchResults();
		   	    	for (int i=0; i<results.size();i++) {
		   	    		LOG.info("=======search result (begin) =========");
		   	    		TimeSeriesSearch.SearchResult sr = results.get(i);
		   	    		LOG.info("id: " + sr.id + " offset: " + sr.offset +  " distance: " + sr.distance);
		   	    			
		   	    	}
		   	    	
		   	    	LOG.info("=======search result (end) =========");
			  }
		 }
	
	public static void main (String[] args) throws Exception  {
		ZMQ.Context context = ZMQ.context(1); //1 is for one single IO thread per socket. 
		SimulatedAggregator aggregator = new SimulatedAggregator(context);
		aggregator.start();
		try {
	    	   //take some sleep, before doing the actual send action 
	    	   Thread.sleep (500); 
	    }
	    catch (Throwable t) {
	    		//do some logging 
	    }
	    
		int threadCount = 20;
		for (int i=0; i<threadCount;i++) {
		  int processId = 10000 + i;
		  SimulatedPartitioner  partitioner = new SimulatedPartitioner (context, processId);
		  partitioner.start();
	   }
	   
	    aggregator.join();
		
		LOG.info("done with the partitioner/aggregator message passing");
		
		context.term();
		
		
		
	}
}


