/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.start

import android.os.Binder
import android.os.Bundle
import fr.sygix.tsuyu.ipc.ITsuyuServer

abstract class TestServerStub : ITsuyuServer.Stub() {
    override fun getClientService() = Binder()
    override fun getRequest(token: String) = Bundle()
    override fun confirm(token: String, allowed: Boolean) = Unit
    override fun getAccess() = Bundle()
    override fun revoke(uid: Int) = Unit
    override fun getPersistence() = Bundle()
    override fun preparePersistence() = Bundle()
    override fun disableKeyExpiry() = Bundle()
}
