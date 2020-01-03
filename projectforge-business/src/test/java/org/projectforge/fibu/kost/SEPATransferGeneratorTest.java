/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.fibu.kost;

import org.junit.jupiter.api.Test;
import org.projectforge.business.fibu.EingangsrechnungDO;
import org.projectforge.business.fibu.EingangsrechnungsPositionDO;
import org.projectforge.business.fibu.PaymentType;
import org.projectforge.business.fibu.kost.reporting.SEPATransferGenerator;
import org.projectforge.business.fibu.kost.reporting.SEPATransferResult;
import org.projectforge.framework.persistence.user.entities.TenantDO;
import org.projectforge.test.AbstractTestBase;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SEPATransferGeneratorTest extends AbstractTestBase {
  @Test
  public void testIban() {
    String iban = "DE12 3456 7890 1234 5678 90";
    String bic = null;
    String xml = testIban(iban, null);
    assertNotNull(xml);
    assertTrue(xml.contains("<IBAN>" + iban.replaceAll("\\s", "") + "</IBAN>"), "xml: " + xml);
    assertTrue(xml.contains("<Nm>ACME INC.</Nm>"), "xml: " + xml);
    assertTrue(xml.contains("<Nm>Kai Reinhard</Nm>"), "xml: " + xml);
    assertTrue(xml.contains("<IBAN>DE87200500001234567890</IBAN>"), "xml: " + xml);
    xml = testIban("IT12 3456 7890 1234 5678 90", null);
    assertNull(xml, "xml: " + xml);
    iban = "IT12 3456 7890 1234 5678 90";
    bic = "UNCRITM1J27";
    xml = testIban(iban, bic);
    assertNotNull("xml: " + xml, xml);
    assertTrue(xml.contains("<IBAN>" + iban.replaceAll("\\s", "") + "</IBAN>"), "xml: " + xml);
    assertTrue(xml.contains("<BIC>" + bic + "</BIC>"), "xml: " + xml);
  }

  private String testIban(String iban, String bic) {
    SEPATransferGenerator generator = new SEPATransferGenerator();
    TenantDO tenant = new TenantDO();
    tenant.setName("ACME INC.");
    EingangsrechnungDO invoice = new EingangsrechnungDO();
    invoice.setIban(iban);
    invoice.setBic(bic);
    invoice.setTenant(tenant);
    invoice.setPaymentType(PaymentType.BANK_TRANSFER);
    invoice.setReceiver("Kai Reinhard");
    invoice.setReferenz("Consulting ProjectForge");
    EingangsrechnungsPositionDO position = new EingangsrechnungsPositionDO();
    position.setEingangsrechnung(invoice);
    position.setTenant(tenant);
    position.setEinzelNetto(new BigDecimal(100));
    invoice.addPosition(position);
    SEPATransferResult result = generator.format(invoice);
    if (result.getXml() == null) {
      return null;
    }
    return new String(result.getXml(), StandardCharsets.UTF_8);
  }
}
