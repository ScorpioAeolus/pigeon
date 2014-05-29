/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.domain;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.dianping.dpsf.async.ServiceFuture;
import com.dianping.dpsf.exception.DPSFException;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.util.Constants;

public class ServiceFutureImpl extends CallbackFuture implements ServiceFuture {

	private static final Logger logger = LoggerLoader.getLogger(ServiceFutureImpl.class);

	private static final MonitorLogger monitorLogger = ExtensionLoader.getExtension(Monitor.class).getLogger();

	private long timeout = Long.MAX_VALUE;

	private Thread callerThread;

	public ServiceFutureImpl(long timeout) {
		super();
		this.timeout = timeout;
		callerThread = Thread.currentThread();
	}

	@Override
	public Object _get() throws InterruptedException {
		return _get(this.timeout);
	}

	@Override
	public Object _get(long timeoutMillis) throws InterruptedException {
		InvocationResponse res = null;
		monitorLogger.logEvent("PigeonCall.future", "", "timeout=" + timeoutMillis);
		try {
			res = super.get(timeoutMillis);
		} catch (Exception e) {
			DPSFException dpsfException = null;
			if (e instanceof DPSFException) {
				dpsfException = (DPSFException) e;
			} else {
				dpsfException = new DPSFException(e);
			}
			logger.error(dpsfException);
			monitorLogger.logError(dpsfException);
			throw dpsfException;
		}
		if (res.getMessageType() == Constants.MESSAGE_TYPE_SERVICE) {
			return res.getReturn();
		} else if (res.getMessageType() == Constants.MESSAGE_TYPE_EXCEPTION) {
			logger.error(res.getCause());
			DPSFException dpsfE = new DPSFException(res.getCause());
			monitorLogger.logError(dpsfE);
			throw dpsfE;
		} else if (res.getMessageType() == Constants.MESSAGE_TYPE_SERVICE_EXCEPTION) {
			DPSFException dpsfE = new DPSFException((Throwable) res.getReturn());
			monitorLogger.logError(dpsfE);
			throw dpsfE;
		} else {
			DPSFException e = new DPSFException("error messageType:" + res.getMessageType());
			monitorLogger.logError(e);
			throw e;
		}

	}

	@Override
	public Object _get(long timeout, TimeUnit unit) throws InterruptedException {
		return _get(unit.toMillis(timeout));
	}

	protected void processContext() {
		Thread currentThread = Thread.currentThread();
		if (currentThread == callerThread) {
			super.processContext();
		}
	}

}
