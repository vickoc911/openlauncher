// app/src/main/aidl/com/syu/ipc/IRemoteToolkit.aidl
package com.syu.ipc;

import com.syu.ipc.IRemoteModule;

interface IRemoteToolkit {
    IRemoteModule getRemoteModule(int moduleId);
}
