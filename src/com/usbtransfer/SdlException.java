package com.usbtransfer;



public enum SdlExceptionCause {
	BLUETOOTH_ADAPTER_NULL,
	BLUETOOTH_DISABLED,
	BLUETOOTH_SOCKET_UNAVAILABLE,
	HEARTBEAT_PAST_DUE,
	INCORRECT_LIFECYCLE_MODEL,
	INVALID_ARGUMENT,
	INVALID_RPC_PARAMETER,
	PERMISSION_DENIED,
	RESERVED_CORRELATION_ID,
	SDL_CONNECTION_FAILED,
	SDL_PROXY_CYCLED,
	SDL_PROXY_DISPOSED,
	SDL_PROXY_OBSOLETE,
	SDL_REGISTRATION_ERROR,
	SDL_UNAVAILABLE,
	INVALID_HEADER,
	DATA_BUFFER_NULL,
	SDL_USB_DETACHED,
	SDL_USB_PERMISSION_DENIED,
	LOCK_SCREEN_ICON_NOT_SUPPORTED,
	;
}

public class SdlException extends Exception {
	
	private static final long serialVersionUID = 5922492291870772815L;
	
	protected Throwable detail = null;
	private SdlExceptionCause _sdlExceptionCause = null;
	
	public SdlException(String msg, SdlExceptionCause exceptionCause) {
		super(msg);
		_sdlExceptionCause = exceptionCause;
	}
	
	public SdlException(String msg, Throwable ex, SdlExceptionCause exceptionCause) {
		super(msg + " --- Check inner exception for diagnostic details");
		detail = ex;
		_sdlExceptionCause = exceptionCause;
	}
	
	public SdlException(Throwable ex) {
		super(ex.getMessage());
		detail = ex;
	}
	
	public SdlExceptionCause getSdlExceptionCause() {
		return _sdlExceptionCause;
	}
	
	public Throwable getInnerException() {
		return detail;
	}
	
	public String toString() {
		String ret = this.getClass().getName();
		ret += ": " + this.getMessage();
		if(this.getSdlExceptionCause() != null){
			ret += "\nSdlExceptionCause: " + this.getSdlExceptionCause().name();
		}
		if (detail != null) {
			ret += "\nnested: " + detail.toString();
			detail.printStackTrace();
		}
		return ret;
	}
}
