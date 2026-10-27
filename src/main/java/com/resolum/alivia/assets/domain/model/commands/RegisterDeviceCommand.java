package com.resolum.alivia.assets.domain.model.commands;

public record RegisterDeviceCommand(Long businessId,String macAddress, String deviceType) {

    public RegisterDeviceCommand{
        if (businessId==null||businessId<=0){}
        if (macAddress==null||macAddress.isBlank()){}
        if (deviceType==null||deviceType.isBlank()){}
    }
}
