/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package flink.graphs;

import java.io.BufferedReader;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.spargel.java.MessageIterator;
import org.apache.flink.spargel.java.MessagingFunction;
import org.apache.flink.spargel.java.VertexUpdateFunction;
import org.apache.flink.test.testdata.ConnectedComponentsData;
import org.apache.flink.test.util.JavaProgramTestBase;
import org.apache.flink.types.NullValue;

@SuppressWarnings("serial")
public class TestVertexCentricConnectedComponents extends JavaProgramTestBase {

	private static final long SEED = 9487520347802987L;
	
	private static final int NUM_VERTICES = 1000;
	
	private static final int NUM_EDGES = 10000;

	private String resultPath;
	
	
	@Override
	protected void preSubmit() throws Exception {
		resultPath = getTempFilePath("results");
	}
	
	@Override
	protected void testProgram() throws Exception {
		ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
		
		DataSet<Long> vertexIds = env.generateSequence(1, NUM_VERTICES);
		DataSet<String> edgeString = env.fromElements(ConnectedComponentsData.getRandomOddEvenEdges(NUM_EDGES, NUM_VERTICES, SEED).split("\n"));
		
		DataSet<Tuple3<Long, Long, NullValue>> edges = edgeString.map(new EdgeParser());
		
		DataSet<Tuple2<Long, Long>> initialVertices = vertexIds.map(new IdAssigner());
		Graph<Long, Long, NullValue> graph = Graph.create(initialVertices, edges, env); 
		
		Graph<Long, Long, NullValue> result = graph.runVertexCentricIteration(new CCUpdater(), new CCMessager(), 100);
		
		result.getVertices().writeAsCsv(resultPath, "\n", " ");
		env.execute();
	}
	
	public static final class CCUpdater extends VertexUpdateFunction<Long, Long, Long> {
		@Override
		public void updateVertex(Long vertexKey, Long vertexValue, MessageIterator<Long> inMessages) {
			long min = Long.MAX_VALUE;
			for (long msg : inMessages) {
				min = Math.min(min, msg);
			}
			if (min < vertexValue) {
				setNewVertexValue(min);
			}
		}
	}
	
	public static final class CCMessager extends MessagingFunction<Long, Long, Long, NullValue> {
		@Override
		public void sendMessages(Long vertexId, Long componentId) {
			sendMessageToAllNeighbors(componentId);
		}
	}
	
	/**
	 * A map function that takes a Long value and creates a 2-tuple out of it:
	 * <pre>(Long value) -> (value, value)</pre>
	 */
	public static final class IdAssigner implements MapFunction<Long, Tuple2<Long, Long>> {
		@Override
		public Tuple2<Long, Long> map(Long value) {
			return new Tuple2<Long, Long>(value, value);
		}
	}

	@Override
	protected void postSubmit() throws Exception {
		for (BufferedReader reader : getResultReader(resultPath)) {
			ConnectedComponentsData.checkOddEvenResult(reader);
		}
	}
	
	public static final class EdgeParser extends RichMapFunction<String, Tuple3<Long, Long, NullValue>> {
		public Tuple3<Long, Long, NullValue> map(String value) {
			String[] nums = value.split(" ");
			return new Tuple3<Long, Long, NullValue>(Long.parseLong(nums[0]), Long.parseLong(nums[1]), 
					NullValue.getInstance());
		}
	}
}