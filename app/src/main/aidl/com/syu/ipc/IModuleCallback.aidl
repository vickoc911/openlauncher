// app/src/main/aidl/com/syu/ipc/IModuleCallback.aidl
package com.syu.ipc;

interface IModuleCallback {
    void update(int updateCode, in int[] ints, in float[] floats, in String[] strings);
}
