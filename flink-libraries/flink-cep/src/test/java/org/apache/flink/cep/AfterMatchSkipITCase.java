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

package org.apache.flink.cep;

import org.apache.flink.cep.nfa.AfterMatchSkipStrategy;
import org.apache.flink.cep.pattern.MalformedPatternException;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.util.StreamingMultipleProgramsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;

/**
 * After match skip tests.
 */
public class AfterMatchSkipITCase extends StreamingMultipleProgramsTestBase {

	private String resultPath;
	private String expected;

	private String lateEventPath;
	private String expectedLateEvents;

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Before
	public void before() throws Exception {
		resultPath = tempFolder.newFile().toURI().toString();
		expected = "";

		lateEventPath = tempFolder.newFile().toURI().toString();
		expectedLateEvents = "";
	}

	@After
	public void after() throws Exception {
		compareResultsByLinesInMemory(expected, resultPath);
		compareResultsByLinesInMemory(expectedLateEvents, lateEventPath);
	}

	private PatternSelectFunction<Event, String> newIdSelectFunction(String ... names) {
		return new PatternSelectFunction<Event, String>() {

			@Override
			public String select(Map<String, List<Event>> pattern) {
				StringBuilder builder = new StringBuilder();
				for (String name: names) {
					for (Event e : pattern.get(name)) {
						builder.append(e.getId()).append(",");
					}
				}
				return builder.toString();
			}
		};
	}

	@Test
	public void testSkipToNext() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "a", 0.0),
			new Event(2, "a", 0.0),
			new Event(3, "a", 0.0),
			new Event(4, "a", 0.0),
			new Event(5, "a", 0.0),
			new Event(6, "a", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
			new AfterMatchSkipStrategy(AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_NEXT_EVENT))
			.where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().equals("a");
			}
		}).times(3);
		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "1,2,3,\n2,3,4,\n3,4,5,\n4,5,6,";

		env.execute();
	}

	@Test
	public void testSkipPastLast() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "a", 0.0),
			new Event(2, "a", 0.0),
			new Event(3, "a", 0.0),
			new Event(4, "a", 0.0),
			new Event(5, "a", 0.0),
			new Event(6, "a", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
			new AfterMatchSkipStrategy(AfterMatchSkipStrategy.SkipStrategy.SKIP_PAST_LAST_EVENT)).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().equals("a");
			}
		}).times(3);

		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "1,2,3,\n4,5,6,";

		env.execute();
	}

	@Test
	public void testSkipToFirst() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "ab", 0.0),
			new Event(2, "ab", 0.0),
			new Event(3, "ab", 0.0),
			new Event(4, "ab", 0.0),
			new Event(5, "ab", 0.0),
			new Event(6, "ab", 0.0),
			new Event(7, "ab", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start",
			new AfterMatchSkipStrategy(
				AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_FIRST, "end"))
			.where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).times(2).next("end").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("b");
			}
		}).times(2);

		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start", "end"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "1,2,3,4,\n3,4,5,6,";

		env.execute();
	}

	@Test
	public void testSkipToLast() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "ab", 0.0),
			new Event(2, "ab", 0.0),
			new Event(3, "ab", 0.0),
			new Event(4, "ab", 0.0),
			new Event(5, "ab", 0.0),
			new Event(6, "ab", 0.0),
			new Event(7, "ab", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_LAST, "end")).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).times(2).next("end").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("b");
			}
		}).times(2);
		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start", "end"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "1,2,3,4,\n4,5,6,7,";

		env.execute();
	}

	@Test(expected = MalformedPatternException.class)
	public void testSkipToLastWithEmptyException() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "ab", 0.0),
			new Event(2, "c", 0.0),
			new Event(3, "ab", 0.0),
			new Event(4, "c", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_LAST, "middle")).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).next("middle").where(
			new SimpleCondition<Event>() {

				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("d");
				}
			}
		).oneOrMore().optional().next("end").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("c");
			}
		});
		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start", "end"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();
	}

	@Test(expected = MalformedPatternException.class)
	public void testSkipToLastWithInfiniteLoopException() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "ab", 0.0),
			new Event(2, "c", 0.0),
			new Event(3, "ab", 0.0),
			new Event(4, "c", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_LAST, "start")).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).next("middle").where(
			new SimpleCondition<Event>() {

				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("d");
				}
			}
		).oneOrMore().optional().next("end").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("c");
			}
		});
		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("start", "end"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();
	}

	@Test(expected = MalformedPatternException.class)
	public void testSkipToFirstWithInfiniteLoopException() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "ab", 0.0),
			new Event(2, "c", 0.0),
			new Event(3, "ab", 0.0),
			new Event(4, "c", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("start", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_TO_FIRST, "middle")).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("x");
			}
		}).oneOrMore().optional().next("middle").where(
			new SimpleCondition<Event>() {

				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("b");
				}
			}
		).next("end").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("c");
			}
		});

		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("middle", "end"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		env.execute();
	}

	@Test
	public void testSkipPastLast2() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "a1", 0.0),
			new Event(2, "a2", 0.0),
			new Event(3, "b1", 0.0),
			new Event(4, "b2", 0.0),
			new Event(5, "c1", 0.0),
			new Event(6, "c2", 0.0),
			new Event(7, "d1", 0.0),
			new Event(8, "d2", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("a", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_PAST_LAST_EVENT)).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).followedByAny("b").where(
			new SimpleCondition<Event>() {

				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("b");
				}
			}
		).followedByAny("c").where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("c");
			}
		})
			.followedByAny("d").where(new SimpleCondition<Event>() {
				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("d");
				}
			});

		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("a", "b", "c", "d"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "1,3,5,7,\n1,3,6,7,\n1,4,5,7,\n1,4,6,7,\n2,3,5,7,\n2,3,6,7,\n2,4,5,7,\n2,4,6,7,";

		env.execute();
	}

	@Test
	public void testSkipPastLast3() throws Exception {
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		DataStream<Event> input = env.fromElements(
			new Event(1, "a1", 0.0),
			new Event(2, "c", 0.0),
			new Event(3, "a2", 0.0),
			new Event(4, "b2", 0.0)
		);
		Pattern<Event, ?> pattern = Pattern.<Event>begin("a", new AfterMatchSkipStrategy(
			AfterMatchSkipStrategy.SkipStrategy.SKIP_PAST_LAST_EVENT)).where(new SimpleCondition<Event>() {

			@Override
			public boolean filter(Event value) throws Exception {
				return value.getName().contains("a");
			}
		}).next("b").where(
			new SimpleCondition<Event>() {

				@Override
				public boolean filter(Event value) throws Exception {
					return value.getName().contains("b");
				}
			}
		);

		DataStream<String> result = CEP.pattern(input, pattern).select(newIdSelectFunction("a", "b"));

		result.writeAsText(resultPath, FileSystem.WriteMode.OVERWRITE);

		// expected sequence of matching event ids
		expected = "3,4,";

		env.execute();
	}

}
