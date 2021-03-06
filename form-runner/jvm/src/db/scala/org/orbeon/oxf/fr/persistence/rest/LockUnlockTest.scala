/**
 * Copyright (C) 2017 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.persistence.rest

import org.junit.Test
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.relational.rest.LockInfo
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging}
import org.scalatest.junit.AssertionsForJUnit

class LockUnlockTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[LockUnlockTest]), true)

  @Test def leaseTest(): Unit = {
    Connect.withOrbeonTables("lease") { (connection, provider) ⇒
      if (false) {
        val dataURL = HttpRequest.crudURLPrefix(provider) + "data/123/data.xml"
        val homerLockInfo = LockInfo("hsimpson", Some("simpsons"))
        val margeLockInfo = LockInfo("msimpson", Some("simpsons"))
        HttpAssert.lock(dataURL, homerLockInfo, 200)
        HttpAssert.lock(dataURL, margeLockInfo, 423)
        HttpAssert.lock(dataURL, homerLockInfo, 200)
      }
    }
  }

}
