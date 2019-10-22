package com.xiaoji.duan.aah;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.xiaoji.duan.aah.operation.When;

import io.vertx.amqpbridge.AmqpBridge;
import io.vertx.amqpbridge.AmqpBridgeOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends AbstractVerticle {

	private WebClient webclient = null;
	private AmqpBridge bridge = null;
	private AmqpBridge remote = null;
	private MongoClient mongodb = null;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		webclient = WebClient.create(vertx);

		JsonObject config = new JsonObject();
		config.put("host", "mongodb");
		config.put("port", 27017);
		config.put("keepAlive", true);
		mongodb = MongoClient.createShared(vertx, config);

		bridge = AmqpBridge.create(vertx);

		bridge.endHandler(handler -> {
			connectStompServer();
		});
		connectStompServer();

		AmqpBridgeOptions remoteOption = new AmqpBridgeOptions();
		remoteOption.setReconnectAttempts(60);			// 重新连接尝试60次
		remoteOption.setReconnectInterval(60 * 1000);	// 每次尝试间隔1分钟
		
		remote = AmqpBridge.create(vertx, remoteOption);

		remote.endHandler(handler -> {
			connectRemoteServer();
		});
		connectRemoteServer();

		Router router = Router.router(vertx);
		
		router.route("/aah/jobflow/refresh").handler(BodyHandler.create());
		router.route("/aah/jobflow/refresh").handler(ctx -> this.refresh(ctx));

		HttpServerOptions option = new HttpServerOptions();
		option.setCompressionSupported(true);

		vertx.createHttpServer(option).requestHandler(router::accept).listen(8080, http -> {
			if (http.succeeded()) {
				startFuture.complete();
				System.out.println("HTTP server started on http://localhost:8080");
			} else {
				startFuture.fail(http.cause());
			}
		});
	}

	private void refresh(RoutingContext ctx) {
		
		if (ctx != null) {
			bridge.close(close -> {
				System.out.println("Close amqp connection for refresh jobflows.");
				
				if (close.failed()) {
					close.cause().printStackTrace();
				}
				
				connectStompServer();

			});
			ctx.response().putHeader("Content-Type", "application/json; charset=utf-8").end("{}");
			
			return;
		}
		
		vertx.executeBlocking(future -> {
			System.out.println("Start loading jobflow definitions.");
			mongodb.find("aah_jobflows", new JsonObject(), find -> {
				if (find.succeeded()) {
					List<JsonObject> jobflows = find.result();

					System.out.println("Predefined jobflows count " + (jobflows == null ? 0 : jobflows.size()) + ".");

					for (JsonObject jobflow : jobflows) {
						String firstTrigger = jobflow.getString("trigger");

						subscribeTrigger(bridge, firstTrigger, jobflow);
						System.out.println("Predefined job flow " + jobflow.getString("name") + "[" + firstTrigger + "] started.");
					}
					
				} else {
					System.out.println("No defined job flows.");
				}

				future.complete("completed");
			});
		}, complete -> {
			System.out.println("The result is: " + complete.result());

			if (ctx != null) {
				ctx.response().putHeader("Content-Type", "application/json; charset=utf-8").end("{}");
			}
		});
	}
	
	private void refreshRemote(RoutingContext ctx) {
		
		if (ctx != null) {
			remote.close(close -> {
				System.out.println("Close amqp connection for refresh jobflows.");
				
				if (close.failed()) {
					close.cause().printStackTrace();
				}
				
				connectRemoteServer();

			});
			ctx.response().putHeader("Content-Type", "application/json; charset=utf-8").end("{}");
			
			return;
		}
		
		vertx.executeBlocking(future -> {
			System.out.println("Start loading jobflow definitions.");
			mongodb.find("aah_jobflows", new JsonObject(), find -> {
				if (find.succeeded()) {
					List<JsonObject> jobflows = find.result();

					System.out.println("Predefined jobflows count " + (jobflows == null ? 0 : jobflows.size()) + ".");

					for (JsonObject jobflow : jobflows) {
						String firstTrigger = jobflow.getString("trigger");

						subscribeTrigger(remote, firstTrigger, jobflow);
						System.out.println("Predefined job flow " + jobflow.getString("name") + "[" + firstTrigger + "] started.");
					}
					
				} else {
					System.out.println("No defined job flows.");
				}

				future.complete("completed");
			});
		}, complete -> {
			System.out.println("The result is: " + complete.result());

			if (ctx != null) {
				ctx.response().putHeader("Content-Type", "application/json; charset=utf-8").end("{}");
			}
		});
	}
	
	private void connectStompServer() {
		bridge.start(config().getString("stomp.server.host", "sa-amq"),
				config().getInteger("stomp.server.port", 5672), res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						connectStompServer();
					} else {
						refresh(null);
					}
				});
	}
	
	private void connectRemoteServer() {
		remote.start(config().getString("remote.server.host", "sa-amq"),
				config().getInteger("remote.server.port", 5672), res -> {
					if (res.failed()) {
						res.cause().printStackTrace();
						connectRemoteServer();
					} else {
						refreshRemote(null);
					}
				});
	}
	
	private void subscribeTrigger(AmqpBridge bridge, String trigger, JsonObject jobflow) {
		MessageConsumer<JsonObject> consumer = bridge.createConsumer(trigger);
		System.out.println("jobflow [" + jobflow.getString("name") + "][" + trigger + "] subscribed.");
		consumer.handler(vertxMsg -> this.process(trigger, jobflow, vertxMsg));
	}
	
	private static String currentDateTime() {
		SimpleDateFormat myFmt = new SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒");
		return myFmt.format(new Date());
	}
	
	private void process(String trigger, JsonObject jobflow, Message<JsonObject> received) {
		System.out.println("jobflow [" + jobflow.getString("name") + "] triggered by " + trigger + "@" + currentDateTime());
		String instanceId = UUID.randomUUID().toString();
		System.out.println("jobflow [" + jobflow.getString("name") + "] instance id " + instanceId);
		System.out.println(jobflow.encodePrettily());
		System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "] parameters [" + getShortContent(received.body().encode()) + "]");
		if (!(received.body().getValue("body") instanceof JsonObject)) {
			System.out.println("Message content is not JsonObject, process stopped.");
			return;
		}
		
		JsonObject data = received.body().getJsonObject("body");
		
		JsonArray parameters = jobflow.getJsonArray("parameters");
		
		JsonObject root = new JsonObject();
		
		if (data != null) {
			JsonObject rootparams = new JsonObject();
			for (int i = 0; i < parameters.size(); i++) {
				String parameter = parameters.getString(i);

				if (data.getJsonObject("context") == null)
					rootparams.put(parameter, "");
				else
					rootparams.put(parameter, data.getJsonObject("context").getValue(parameter, ""));
			}
			
			root.put("parameters", rootparams);
		}

		String name = jobflow.getString("name");
		root.put("timestamp", System.currentTimeMillis());
		persistentStatus(trigger, name, instanceId, jobflow, instanceId, null, null, null, root);

		JsonArray follows = jobflow.getJsonArray("follows");
		for (int i = 0; i < follows.size(); i++) {
			JsonObject followtask = follows.getJsonObject(i);
			
			String taskname = followtask.getString("name", "");
			String type = followtask.getString("type", "single");
			
			JsonObject when = followtask.getJsonObject("when", new JsonObject());
			
			if (!when.isEmpty()) {
				JsonObject whendata = new JsonObject();
				whendata.put("root", root);
				whendata.put("parent", root);

				When wheneval = new When(when, whendata);
				boolean whengo = true;
				
				try {
					whengo = wheneval.evalate();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
				
				if (!whengo) {
					System.out.println(taskname + " skipped for when condition.");
					continue;
				} else {
					System.out.println(taskname + " continued for when condition.");
				}
			}

			if ("loop".equals(type)) {
				System.out.println("follows can not process loop task, skipped.");
				continue;
			}
			
			if ("composite".equals(type)) {
				JsonArray compositeWith = followtask.getJsonArray("composite_with", new JsonArray());
				JsonObject task = followtask.getJsonObject("task", new JsonObject());
				
				if (!compositeWith.isEmpty()) {
					List<Future<JsonObject>> compositeFutures = new LinkedList<>();
					
					for (int pos = 0; pos < compositeWith.size(); pos++) {
						Future<JsonObject> endpointFuture = Future.future();
						compositeFutures.add(endpointFuture);
						
						String endpoint = compositeWith.getString(pos);
						
						String taskresulttrigger = instanceId + "_" + endpoint;
	
						// 订阅混合处理返回
						MessageConsumer<JsonObject> consumer = bridge.createConsumer(taskresulttrigger);
						System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + endpoint + "] composite endpoint subscribed.");
						consumer.exceptionHandler(exception -> endpointFuture.fail(exception));
						consumer.handler(vertxMsg -> {
							JsonObject endin = vertxMsg.body().getJsonObject("body", new JsonObject()).getJsonObject("context", new JsonObject());
							endpointFuture.complete(endin);
							
							vertx.setTimer(3000, timer -> {
								consumer.unregister(ar -> {
									if (ar.succeeded()) {
										System.out.println("Consumer " + consumer.address() + " unregister succeeded.");
									} else {
										ar.cause().printStackTrace();
									}
								});
							});
						});
					}
					
					// 所有混合处理返回后处理
					CompositeFuture.all(Arrays.asList(compositeFutures.toArray(new Future[compositeFutures.size()])))
					.map(v -> compositeFutures.stream().map(Future::result).collect(Collectors.toList()))
					.setHandler(handler -> {
						if (handler.succeeded()) {
							System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "] composite endpoint completed.");
//							System.out.println(task.encode());
							JsonObject current = new JsonObject();
							JsonObject parent = new JsonObject();

							List<JsonObject> results = handler.result();

							JsonObject outputs = new JsonObject();
							for (JsonObject result : results) {
								outputs.mergeIn(result);
							}
							parent.put("outputs", outputs);
							current.put("parent", parent);

							trigger(null, instanceId, root, current, jobflow, followtask, instanceId, task);
						} else {
							System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "] composite endpoint failed.");
							handler.cause().printStackTrace();
						}
					});
				}
				
				continue;
			}
			
			trigger(null, instanceId, root, root.copy(), jobflow, jobflow.copy(), instanceId, followtask);
		}
	}

	public static String getShortContent(String origin) {
		return origin.length() > 512 ? origin.substring(0, 512) : origin;
	}
	
	/**
	 * 
	 * next() -> nexts()
	 * 
	 * @param instanceId
	 * @param triggerId
	 * @param root
	 * @param parent
	 * @param current
	 * @param jobflow
	 * @param parenttask
	 * @param parentTriggerId
	 * @param task
	 */
	private void nexts(String instanceId, String triggerId, JsonObject root, JsonObject parent, JsonObject current, JsonObject jobflow, JsonObject parenttask, String parentTriggerId, JsonObject task) {
		String trigger = task.getString("trigger");
		JsonArray nexts = task.getJsonArray("next");

		// 没有子任务完成后处理
		for (int i = 0; i < nexts.size(); i++) {
			JsonObject nexttask = nexts.getJsonObject(i);
			
			String taskname = nexttask.getString("name", "");
			String tasktype = nexttask.getString("type", "single");
			
			JsonObject when = nexttask.getJsonObject("when", new JsonObject());
			
			if (!when.isEmpty()) {
				JsonObject whendata = new JsonObject();
				whendata.put("root", root);
				whendata.put("parent", parent);

				When wheneval = new When(when, whendata);
				boolean whengo = true;
				
				try {
					whengo = wheneval.evalate();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
				
				if (!whengo) {
					System.out.println(taskname + " skipped for when condition.");
					continue;
				} else {
					System.out.println(taskname + " continued for when condition.");
				}
			}

			if ("single".equals(tasktype)) {
				trigger(null, instanceId, root, current, jobflow, task, triggerId, nexttask);
			} else if ("forward".equals(tasktype)) {
				JsonArray forwardWith = nexttask.getJsonArray("forward_with", new JsonArray());
				
				if (!forwardWith.isEmpty()) {
					for (int pos = 0; pos < forwardWith.size(); pos ++) {
						String endpoint = forwardWith.getString(pos);
						
						String endpointtrigger = instanceId + "_" + endpoint;

						// 发送混合处理完成消息
						MessageProducer<JsonObject> producer = bridge.createProducer(endpointtrigger);

						JsonObject body = new JsonObject().put("context", new JsonObject().put(endpoint, current.getJsonObject("parent", current).getJsonObject("outputs", current)));
						System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + endpoint + "] composite endpoint send " + getShortContent(body.encode()));

						producer.send(new JsonObject().put("body", body));
						producer.end();
					}
				} else {
					System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "] composite endpoint no defined forward withs.");
				}
				
			} else if ("loop".equals(tasktype)) {
				JsonObject persistent = new JsonObject();
				persistent.put("root", root);
				persistent.put("parent", parent);

				Configuration document = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
				
				String[] variableparams = nexttask.getString("variable").split(";");
				String variable = variableparams[0];
				net.minidev.json.JSONArray variableValues = new net.minidev.json.JSONArray();
				
				try {
					variableValues = JsonPath.using(document).parse(persistent.encode()).read(variableparams[1]);
				} catch (Exception e) {
					System.out.println(taskname + " skipped for loop values error " + e.getMessage() + ".");
					continue;
				}
				
				int start = (nexttask.getString("start") == null || !nexttask.getString("start").matches("[0-9]+")) ? 0 : Integer.valueOf(nexttask.getString("start"));
				int end = (nexttask.getString("end") == null || !nexttask.getString("end").matches("[0-9]+")) ? variableValues.size() : Integer.valueOf(nexttask.getString("end"));

				JsonObject looptask = nexttask.getJsonObject("task");
				// 控制循环任务结束
				JsonObject complete = nexttask.getJsonObject("complete", new JsonObject());
				// 全部成功返回处理
				JsonObject allcomplete = complete.getJsonObject("all", new JsonObject());
				
				if (allcomplete.isEmpty()) {
					for (int loop = start; loop < end; loop++) {
						String loopvalue = "##" + variable + "_value##";
						JsonObject looptaskins = new JsonObject(looptask.encode().replaceAll(loopvalue, String.valueOf(loop)));

						String loopstring = "##" + variable + "_string##";
						looptaskins = new JsonObject(looptaskins.encode().replaceAll(loopstring, String.valueOf(variableValues.get(loop))));

						trigger(null, instanceId, root, current, jobflow, task, triggerId, looptaskins);
					}
				} else {
					// 全部结束后处理
					List<Future<JsonObject>> loopfutures = new LinkedList<>();
					
					for (int loop = start; loop < end; loop++) {
						String loopvalue = "##" + variable + "_value##";
						JsonObject looptaskins = new JsonObject(looptask.encode().replaceAll(loopvalue, String.valueOf(loop)));

						Future<JsonObject> future = Future.future();
						loopfutures.add(future);
						
						trigger(future, instanceId, root, current, jobflow, task, triggerId, looptaskins);
					}
					
					CompositeFuture.all(Arrays.asList(loopfutures.toArray(new Future[loopfutures.size()])))
					.map(v -> loopfutures.stream().map(Future::result).collect(Collectors.toList()))
					.setHandler(handler -> {
						System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] merged finished.");
						if (handler.succeeded()) {
							List<JsonObject> results = handler.result();
							
							current.getJsonObject("parent").put("loop", new JsonObject().put("outputs", new JsonObject().put("merged", results)));
							
							trigger(null, instanceId, root, current, jobflow, task, triggerId, allcomplete);

						} else {
							System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] complete all stopped with error.");
							handler.cause().printStackTrace();
						}
					});
				}
			}
		}
	
	}
	
	/**
	 * 
	 * next() -> allcompletenexts()
	 * 
	 * @param allcompletenexts
	 * @param instanceId
	 * @param triggerId
	 * @param root
	 * @param parent
	 * @param current
	 * @param jobflow
	 * @param parenttask
	 * @param parentTriggerId
	 * @param task
	 */
	private void allcompletenexts(JsonObject allcompletenexts, String instanceId, String triggerId, JsonObject root, JsonObject parent, JsonObject current, JsonObject jobflow, JsonObject parenttask, String parentTriggerId, JsonObject task) {
		String trigger = task.getString("trigger");
		JsonArray nexts = task.getJsonArray("next");

		// 存在子任务完成后处理
		List<Future<JsonObject>> nextscompletefutures = new LinkedList<>();

		for (int i = 0; i < nexts.size(); i++) {
			JsonObject nexttask = nexts.getJsonObject(i);
			
			String taskname = nexttask.getString("name", "");
			String tasktype = nexttask.getString("type", "single");

			JsonObject when = nexttask.getJsonObject("when", new JsonObject());
			
			if (!when.isEmpty()) {
				JsonObject whendata = new JsonObject();
				whendata.put("root", root);
				whendata.put("parent", parent);

				When wheneval = new When(when, whendata);
				boolean whengo = true;
				
				try {
					whengo = wheneval.evalate();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
				
				if (!whengo) {
					System.out.println(taskname + " skipped for when condition.");
					continue;
				} else {
					System.out.println(taskname + " continued for when condition.");
				}
			}

			Future<JsonObject> nextsfuture = Future.future();
			nextscompletefutures.add(nextsfuture);

			if ("single".equals(tasktype)) {
				trigger(nextsfuture, instanceId, root, current, jobflow, task, triggerId, nexttask);
			} else if ("loop".equals(tasktype)) {
				JsonObject persistent = new JsonObject();
				persistent.put("root", root);
				persistent.put("parent", parent);

				Configuration document = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
				
				String[] variableparams = nexttask.getString("variable").split(";");
				String variable = variableparams[0];
				net.minidev.json.JSONArray variableValues = JsonPath.using(document).parse(persistent.encode()).read(variableparams[1]);
				
				int start = (nexttask.getString("start") == null || !nexttask.getString("start").matches("[0-9]+")) ? 0 : Integer.valueOf(nexttask.getString("start"));
				int end = (nexttask.getString("end") == null || !nexttask.getString("end").matches("[0-9]+")) ? variableValues.size() : Integer.valueOf(nexttask.getString("end"));

				JsonObject looptask = nexttask.getJsonObject("task");
				// 控制循环任务结束
				JsonObject complete = nexttask.getJsonObject("complete", new JsonObject());
				// 全部成功返回处理
				JsonObject allcomplete = complete.getJsonObject("all", new JsonObject());
				
				// 全部结束后处理
				List<Future<JsonObject>> loopfutures = new LinkedList<>();
				
				for (int loop = start; loop < end; loop++) {
					String loopvalue = "##" + variable + "_value##";
					JsonObject looptaskins = new JsonObject(looptask.encode().replaceAll(loopvalue, String.valueOf(loop)));

					Future<JsonObject> future = Future.future();
					loopfutures.add(future);
					
					trigger(future, instanceId, root, current, jobflow, task, triggerId, looptaskins);
				}
				
				CompositeFuture.all(Arrays.asList(loopfutures.toArray(new Future[loopfutures.size()])))
				.map(v -> loopfutures.stream().map(Future::result).collect(Collectors.toList()))
				.setHandler(handler -> {
					System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] merged finished.");
					if (handler.succeeded()) {
						List<JsonObject> results = handler.result();
						
						current.getJsonObject("parent").put("loop", new JsonObject().put("outputs", new JsonObject().put("merged", results)));
						// 所有子任务完成后处理
						nextsfuture.complete(new JsonObject().put("merged", results));
						
						if (!allcomplete.isEmpty()) {
							// 所有循环处理完成后处理
							trigger(null, instanceId, root, current, jobflow, task, triggerId, allcomplete);
						}
					} else {
						System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] complete all stopped with error.");
						handler.cause().printStackTrace();

						// 所有子任务完成后处理
						nextsfuture.complete(new JsonObject().put("merged", new JsonArray()));
					}
				});
			}
		}
		
		// 所有子任务完成后处理
		CompositeFuture.all(Arrays.asList(nextscompletefutures.toArray(new Future[nextscompletefutures.size()])))
		.map(v -> nextscompletefutures.stream().map(Future::result).collect(Collectors.toList()))
		.setHandler(handler -> {
			System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] sub tasks result merged finished.");
			if (handler.succeeded()) {
				List<JsonObject> results = handler.result();
				
				current.getJsonObject("parent").put("nexts", new JsonObject().put("complete", new JsonObject().put("all", new JsonObject().put("outputs", new JsonObject().put("merged", results)))));
			
				// 所有子任务完成后处理
				trigger(null, instanceId, root, current, jobflow, task, triggerId, allcompletenexts);
			} else {
				System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] sub tasks stopped with error.");
				handler.cause().printStackTrace();
			}
		});
	
	}
	
	private void next(Future<JsonObject> futureIn, String instanceId, String triggerId, JsonObject root, JsonObject parent, JsonObject jobflow, JsonObject parenttask, String parentTriggerId, JsonObject task, Message<JsonObject> received, MessageConsumer<JsonObject> _self) {
		//////////////////////////////////////////////////////////
		// 2019/10/14 席理加增加
		// 支持子任务多次返回
		//////////////////////////////////////////////////////////
		Boolean hasMore = Boolean.FALSE;
		//////////////////////////////////////////////////////////

		try {
			Long currenttime = System.currentTimeMillis();
			String trigger = task.getString("trigger");
			System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] outputs [" + getShortContent(received.body().encode()) + "]");
			JsonObject data = received.body().getJsonObject("body");
	
			JsonObject current = new JsonObject();
			
			if (data != null) {
				parent = parent.put("outputs", data.getJsonObject("context"));
				//////////////////////////////////////////////////////////
				// 2019/10/14 席理加增加
				// 支持子任务多次返回
				//////////////////////////////////////////////////////////
				hasMore = data.getBoolean("more", Boolean.FALSE);
				//////////////////////////////////////////////////////////
			}
			current.put("parent", parent);
	
			String rootname = jobflow.getString("name");
			String roottrigger = jobflow.getString("trigger");
			String name = task.getString("name");
			current.put("timestamp", currenttime);
			current.put("costs", currenttime - parent.getLong("timestamp", currenttime));
	
			persistentStatus(roottrigger, rootname, instanceId, parenttask, parentTriggerId, trigger, name, triggerId, current);
	
			// 完成状态返回
			if (futureIn != null)
				futureIn.complete(data.getJsonObject("context"));
			
			JsonArray nexts = task.getJsonArray("next");
			
			// 下一步处理全部完成后处理
			JsonObject completenexts = task.getJsonObject("complete", new JsonObject());
			JsonObject allcompletenexts = completenexts.getJsonObject("next", new JsonObject())
					.getJsonObject("all", new JsonObject());
	
			if (nexts != null && !nexts.isEmpty()) {
				if (!allcompletenexts.isEmpty()) {
					allcompletenexts(allcompletenexts, instanceId, triggerId, root, parent, current, jobflow, parenttask, parentTriggerId, task);
				} else {
					nexts(instanceId, triggerId, root, parent, current, jobflow, parenttask, parentTriggerId, task);
				}
			} else {
				System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] end@" + currentDateTime() + " with no next triggers.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (!hasMore) {
				vertx.setTimer(3000, timer -> {
					_self.unregister(ar -> {
						if (ar.succeeded()) {
							System.out.println("Consumer " + _self.address() + " unregister succeeded.");
						} else {
							ar.cause().printStackTrace();
						}
					});
				});
			}
		}
	}
	
	/**
	 * 
	 * process() -> trigger()
	 * or
	 * next() -> trigger()
	 * 
	 * @param future
	 * @param instanceId
	 * @param root
	 * @param current
	 * @param jobflow
	 * @param parenttask
	 * @param parentTriggerId
	 * @param task
	 */
	private void trigger(Future<JsonObject> future, String instanceId, JsonObject root, JsonObject current, JsonObject jobflow, JsonObject parenttask, String parentTriggerId, JsonObject task) {
		String trigger = task.getString("trigger");
		
		String triggerId = UUID.randomUUID().toString();
		System.out.println("jobflow [" + jobflow.getString("name") + "] trigger[" + trigger + "] id " + triggerId);

		String taskresulttrigger = instanceId + "_" + trigger + "_" + triggerId;

		JsonObject pParameters = new JsonObject();
		
		JsonObject nextctx = new JsonObject();
		nextctx.put("next", taskresulttrigger);
		
		JsonArray parameters = task.getJsonArray("parameters");
		
		JsonObject persistent = new JsonObject();
		persistent.put("root", root);
		persistent.put("parent", current.getJsonObject("parent") == null ? current : current.getJsonObject("parent"));
//		System.out.println("============================ Debug ============================");
//		System.out.println(persistent.encode());
//		System.out.println("============================ Debug ============================");
		if ("mpp".equals(trigger))
		System.out.println(getShortContent(persistent.encode()));

		Configuration document = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();

		for (int i = 0; i < parameters.size(); i++) {
			String parameter = parameters.getString(i);
			
			String[] params = parameter.split(";");
			
			Object val = "";

			if (params.length > 1 && params[1].contains("$")) {
				//增加多个参数合并   $.parent.parameters | $.root.parameters
				if (params[1].contains("|")) {
					String[] merges = params[1].split("\\|");
					
					Object mergeto = "";
					JsonObject merged = new JsonObject();

					int mergeindex = 0;
					for (String mergeval : merges) {
						mergeto = JsonPath.using(document).parse(persistent.encode()).read(mergeval);
						
						if (mergeto instanceof Map) {
							merged.mergeIn(new JsonObject((Map) mergeto));
						} else {
							merged.mergeIn(new JsonObject().put(params[0] + "_" + mergeindex, mergeto));
						}
						
						mergeindex++;
					}
					
					val = merged;
				} else {
					val = JsonPath.using(document).parse(persistent.encode()).read(params[1]);
				}
			} else if (params.length > 1) {
				val = params[1];
			}
			
			if (val == null && params.length > 2) {
				if (params[2].contains("$")) {
					val = JsonPath.using(document).parse(persistent.encode()).read(params[2]);
				} else {
					val = params[2];
				}
			}
			
			nextctx.put(params[0], val);
			pParameters.put(params[0], val);
		}
		
		current.put("parameters", pParameters);
		// 订阅下一步处理返回
		MessageConsumer<JsonObject> consumer = bridge.createConsumer(taskresulttrigger);
		System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] subscribed.");
		consumer.handler(vertxMsg -> this.next(future, instanceId, triggerId, root, current, jobflow, parenttask, parentTriggerId, task, vertxMsg, consumer));

		// 发送下一步处理消息
		MessageProducer<JsonObject> producer = bridge.createProducer(trigger);

		JsonObject body = new JsonObject().put("context", nextctx);
		System.out.println("jobflow [" + jobflow.getString("name") + "][" + instanceId + "][" + trigger + "][" + triggerId + "] send " + (body.encode().length() > 512 ? body.encode().substring(0, 512) : body.encode()));

		producer.send(new JsonObject().put("body", body));
		producer.end();
	}
	
	private void persistentStatus(String trigger, String name, String instanceId, JsonObject parenttask, String parentId, String followtrigger, String followname, String followinstanceId, JsonObject status) {
		vertx.executeBlocking(block -> {
			MessageProducer<JsonObject> producer = bridge.createProducer("aak");
	
			JsonObject body = new JsonObject()
					.put("saprefix", "aah")
					.put("collection", trigger)
					.put("name", name)
					.put("id", instanceId)
					.put("timestamp", status.getLong("timestamp"))
					.put("context", status);
	
			if (status.containsKey("costs")) {
				body.put("costs", status.getLong("costs"));
			}
			
			if (followtrigger != null || followname != null || followinstanceId != null) {
				body.put("parent", parenttask);
				body.put("parentId", parentId);
			}
			
			if (followtrigger != null) {
				body.put("trigger", followtrigger);
			}
			
			if (followname != null) {
				body.put("triggerName", followname);
			}
			
			if (followinstanceId != null) {
				body.put("triggerId", followinstanceId);
			}
	
			producer.send(new JsonObject().put("body", body));
			producer.end();
		}, handler -> {});
	}
	
	private boolean when(JsonObject def, JsonObject persistent) {
		return true;
	}
}
