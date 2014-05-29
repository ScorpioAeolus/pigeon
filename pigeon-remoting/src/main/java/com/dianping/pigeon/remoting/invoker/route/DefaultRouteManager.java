/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.route;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.dianping.dpsf.exception.NoConnectionException;
import com.dianping.pigeon.domain.phase.Disposable;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.registry.RegistryManager;
import com.dianping.pigeon.registry.listener.RegistryEventListener;
import com.dianping.pigeon.registry.listener.ServiceProviderChangeEvent;
import com.dianping.pigeon.registry.listener.ServiceProviderChangeListener;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.listener.ClusterListenerManager;
import com.dianping.pigeon.remoting.invoker.route.balance.ConsistentHashLoadBalance;
import com.dianping.pigeon.remoting.invoker.route.balance.LeastSuccessLoadBalance;
import com.dianping.pigeon.remoting.invoker.route.balance.LoadAutoawareLoadBalance;
import com.dianping.pigeon.remoting.invoker.route.balance.LoadBalance;
import com.dianping.pigeon.remoting.invoker.route.balance.LoadBalanceManager;
import com.dianping.pigeon.remoting.invoker.route.balance.RandomLoadBalance;
import com.dianping.pigeon.remoting.invoker.route.balance.RoundRobinLoadBalance;

public class DefaultRouteManager implements RouteManager, Disposable {

	private static final Logger logger = LoggerLoader.getLogger(DefaultRouteManager.class);

	private static final ClusterListenerManager clusterListenerManager = ClusterListenerManager.getInstance();

	private ServiceProviderChangeListener providerChangeListener = new InnerServiceProviderChangeListener();

	public DefaultRouteManager() {
		RegistryEventListener.addListener(providerChangeListener);
		LoadBalanceManager.register(RandomLoadBalance.NAME, null, RandomLoadBalance.instance);
		LoadBalanceManager.register(LoadAutoawareLoadBalance.NAME, null, LoadAutoawareLoadBalance.instance);
		LoadBalanceManager.register(LeastSuccessLoadBalance.NAME, null, LeastSuccessLoadBalance.instance);
		LoadBalanceManager.register(RoundRobinLoadBalance.NAME, null, RoundRobinLoadBalance.instance);
		LoadBalanceManager.register(ConsistentHashLoadBalance.NAME, null, ConsistentHashLoadBalance.instance);
	}

	public Client route(List<Client> clientList, InvokerConfig<?> invokerConfig, InvocationRequest request) {
		if (logger.isDebugEnabled()) {
			for (Client client : clientList) {
				logger.debug("available service provider：\t" + client.getAddress());
			}
		}
		Boolean isWriteBufferLimit = (Boolean) request.getAttachment(Constants.REQ_ATTACH_WRITE_BUFF_LIMIT);

		isWriteBufferLimit = (isWriteBufferLimit != null ? isWriteBufferLimit : false)
				&& request.getCallType() == Constants.CALLTYPE_NOREPLY;

		List<Client> availableClients = filterWithGroupAndWeight(clientList, invokerConfig, isWriteBufferLimit);

		Client selectedClient = select(availableClients, invokerConfig, request);

		checkClientNotNull(selectedClient, invokerConfig);

		if (!selectedClient.isConnected()) {
			selectedClient.connect();
		}

		while (!selectedClient.isConnected()) {
			clusterListenerManager.removeConnect(selectedClient);
			availableClients.remove(selectedClient);
			if (availableClients.isEmpty()) {
				break;
			}
			selectedClient = select(availableClients, invokerConfig, request);
			checkClientNotNull(selectedClient, invokerConfig);
		}

		if (!selectedClient.isConnected()) {
			throw new NoConnectionException("no available server exists for service[" + invokerConfig + "]");
		}
		return selectedClient;
	}

	/**
	 * 按照权重和分组过滤客户端选择
	 * 
	 * @param clientList
	 * @param serviceName
	 * @param group
	 * @param isWriteBufferLimit
	 * @return
	 */
	public List<Client> filterWithGroupAndWeight(List<Client> clientList, InvokerConfig<?> invokerConfig,
			Boolean isWriteBufferLimit) {
		List<Client> filteredClients = new ArrayList<Client>(clientList.size());
		boolean existClientBuffToLimit = false;
		for (Client client : clientList) {
			String address = client.getAddress();
			if (client.isActive() && RegistryManager.getInstance().getServiceWeight(address) > 0) {
				if (!isWriteBufferLimit || client.isWritable()) {
					filteredClients.add(client);
				} else {
					existClientBuffToLimit = true;
				}
			}
		}
		if (filteredClients.isEmpty()) {
			throw new NoConnectionException("no available server exists for service[" + invokerConfig.getUrl()
					+ "] and group[" + invokerConfig.getGroup() + "]"
					+ (existClientBuffToLimit ? ", and exists some server's write buffer reach limit" : "") + ".");
		}
		return filteredClients;
	}

	private void checkClientNotNull(Client client, InvokerConfig<?> invokerConfig) {
		if (client == null) {
			throw new NoConnectionException("no available server exists for service[" + invokerConfig + "]");
		}
	}

	private Client select(List<Client> availableClients, InvokerConfig<?> invokerConfig, InvocationRequest request) {
		LoadBalance loadBalance = null;
		if (request.getCallType() == Constants.CALLTYPE_NOREPLY) {
			loadBalance = RandomLoadBalance.instance;
		}
		if (loadBalance == null) {
			loadBalance = LoadBalanceManager.getLoadBalance(invokerConfig, request.getCallType());
		}
		if (loadBalance == null) {
			loadBalance = RandomLoadBalance.instance;
		}

		return loadBalance.select(availableClients, request);
	}

	@Override
	public void destroy() {
		RegistryEventListener.removeListener(providerChangeListener);
	}

	class InnerServiceProviderChangeListener implements ServiceProviderChangeListener {
		@Override
		public void hostWeightChanged(ServiceProviderChangeEvent event) {
			RegistryManager.getInstance().setServiceWeight(event.getConnect(), event.getWeight());
		}

		@Override
		public void providerAdded(ServiceProviderChangeEvent event) {
		}

		@Override
		public void providerRemoved(ServiceProviderChangeEvent event) {
		}
	}

}
