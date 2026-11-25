// app/src/main/aidl/com/syu/ipc/IRemoteModule.aidl
package com.syu.ipc;

import com.syu.ipc.IModuleCallback;

interface IRemoteModule {
    void register(IModuleCallback callback, int updateCode, int flag);
    void unregister(IModuleCallback callback, int updateCode);
    void cmd(int cmdCode, in int[] ints, in float[] floats, in String[] strings);
}
