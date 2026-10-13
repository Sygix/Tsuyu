/*
 * Copyright (c) 2021 RikkaW
 * SPDX-License-Identifier: MIT
 */

package moe.shizuku.server;
interface IRemoteProcess {
    ParcelFileDescriptor getOutputStream();
    ParcelFileDescriptor getInputStream();
    ParcelFileDescriptor getErrorStream();
    int waitFor();
    int exitValue();
    void destroy();
    boolean alive();
    boolean waitForTimeout(long timeout, String unit);
}
