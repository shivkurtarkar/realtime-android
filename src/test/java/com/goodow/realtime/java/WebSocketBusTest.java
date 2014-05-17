/*
 * Copyright 2014 Goodow.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.goodow.realtime.java;

import com.goodow.realtime.channel.Bus;
import com.goodow.realtime.channel.Message;
import com.goodow.realtime.channel.MessageHandler;
import com.goodow.realtime.channel.impl.ReconnectBus;
import com.goodow.realtime.core.Handler;
import com.goodow.realtime.core.HandlerRegistration;
import com.goodow.realtime.core.HandlerRegistrations;
import com.goodow.realtime.json.Json;
import com.goodow.realtime.json.JsonObject;

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketBusTest extends TestVerticle {
  private static final Logger log = Logger.getLogger(WebSocketBusTest.class.getName());
  static {
    JavaPlatform.register();
    Logger.getLogger(JavaWebSocket.class.getName()).setLevel(Level.ALL);
  }

  private Bus bus;
  private HandlerRegistrations handlerRegs = new HandlerRegistrations();

  @Override
  public void start() {
    initialize();

    container.deployModule("com.goodow.realtime~realtime-channel~0.5.5-SNAPSHOT",
        new AsyncResultHandler<String>() {
          @Override
          public void handle(AsyncResult<String> asyncResult) {
            assertTrue(asyncResult.succeeded());
            assertNotNull("deploymentID should not be null", asyncResult.result());

            bus = new ReconnectBus("ws://localhost:1986/channel/websocket", null);
            startTests();
          }
        });
  }

  @Test
  public void test() {
    HandlerRegistration openHandlerReg =
        bus.registerLocalHandler(Bus.ON_OPEN, new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> message) {
            log.info("EventBus opened");
          }
        });
    HandlerRegistration closeHandlerReg =
        bus.registerLocalHandler(Bus.ON_CLOSE, new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> message) {
            log.info("EventBus closed");
          }
        });
    HandlerRegistration errorHandlerReg =
        bus.registerLocalHandler(Bus.ON_ERROR, new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> message) {
            log.log(Level.SEVERE, "EventBus Error");
          }
        });

    HandlerRegistration handlerRegistration =
        bus.registerHandler("someaddress", new MessageHandler<JsonObject>() {
          @Override
          public void handle(Message<JsonObject> message) {
            VertxAssert.assertEquals("send1", message.body().getString("text"));

            JsonObject o1 = Json.createObject().set("text", "reply1");
            message.reply(o1, new Handler<Message<JsonObject>>() {
              @Override
              public void handle(Message<JsonObject> message) {
                VertxAssert.assertEquals("reply2", message.body().getString("text"));
                // Assert.assertNull(message.replyAddress());

                handlerRegs.unregisterHandler();
                handlerRegs = null;
                bus.close();
                VertxAssert.testComplete();
              }
            });
          }
        });

    handlerRegs.add(openHandlerReg).add(closeHandlerReg).add(errorHandlerReg).add(
        handlerRegistration);

    JsonObject o1 = Json.createObject().set("text", "send1");
    bus.send("someaddress", o1, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> message) {
        VertxAssert.assertEquals("reply1", message.body().getString("text"));

        JsonObject o1 = Json.createObject().set("text", "reply2");
        message.reply(o1);
      }
    });
  }
}
