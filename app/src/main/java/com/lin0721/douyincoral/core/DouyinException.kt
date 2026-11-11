package com.lin0721.douyincoral.core

/** 抖音接口调用失败的统一异常。 */
open class DouyinException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 服务器回的不是数据而是网页或验证页，说明请求被风控拦下。 */
class RiskControlException(message: String) : DouyinException(message)
