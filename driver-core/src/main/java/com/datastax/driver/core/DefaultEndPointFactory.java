/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2022 ScyllaDB
 *
 * Modified by ScyllaDB
 */
package com.datastax.driver.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEndPointFactory implements EndPointFactory {

  private static final Logger logger = LoggerFactory.getLogger(ControlConnection.class);
  private static final InetAddress BIND_ALL_ADDRESS;

  static {
    try {
      BIND_ALL_ADDRESS = InetAddress.getByAddress(new byte[4]);
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  private volatile Cluster cluster;

  @Override
  public void init(Cluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public EndPoint create(Row peersRow) {
    if (peersRow.getColumnDefinitions().contains("native_address")) {
      InetAddress nativeAddress = peersRow.getInet("native_address");
      int nativePort = peersRow.getInt("native_port");
      InetSocketAddress translateAddress =
          cluster.manager.translateAddress(new InetSocketAddress(nativeAddress, nativePort));
      return new TranslatedAddressEndPoint(translateAddress);
    } else if (peersRow.getColumnDefinitions().contains("native_transport_address")) {
      InetAddress nativeAddress = peersRow.getInet("native_transport_address");
      int nativePort = peersRow.getInt("native_transport_port");
      if (cluster.getConfiguration().getProtocolOptions().getSSLOptions() != null
          && !peersRow.isNull("native_transport_port_ssl")) {
        nativePort = peersRow.getInt("native_transport_port_ssl");
      }
      InetSocketAddress translateAddress =
          cluster.manager.translateAddress(new InetSocketAddress(nativeAddress, nativePort));
      return new TranslatedAddressEndPoint(translateAddress);
    } else {
      InetAddress broadcastAddress =
          peersRow.getColumnDefinitions().contains("peer") ? peersRow.getInet("peer") : null;
      InetAddress rpcAddress =
          peersRow.getColumnDefinitions().contains("rpc_address")
              ? peersRow.getInet("rpc_address")
              : null;
      if (broadcastAddress == null || rpcAddress == null) {
        return null;
      } else if (rpcAddress.equals(BIND_ALL_ADDRESS)) {
        logger.warn(
            "Found host with 0.0.0.0 as rpc_address, "
                + "using broadcast_address ({}) to contact it instead. "
                + "If this is incorrect you should avoid the use of 0.0.0.0 server side.",
            broadcastAddress);
        rpcAddress = broadcastAddress;
      }
      InetSocketAddress translateAddress = cluster.manager.translateAddress(rpcAddress);
      return new TranslatedAddressEndPoint(translateAddress);
    }
  }
}
