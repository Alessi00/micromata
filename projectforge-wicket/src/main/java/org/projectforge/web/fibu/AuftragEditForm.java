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

package org.projectforge.web.fibu;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.convert.IConverter;
import org.projectforge.business.fibu.*;
import org.projectforge.business.task.TaskDO;
import org.projectforge.business.user.UserRightValue;
import org.projectforge.business.utils.CurrencyFormatter;
import org.projectforge.common.StringHelper;
import org.projectforge.framework.access.AccessChecker;
import org.projectforge.framework.i18n.I18nHelper;
import org.projectforge.framework.persistence.user.entities.PFUserDO;
import org.projectforge.framework.utils.NumberHelper;
import org.projectforge.web.task.TaskSelectPanel;
import org.projectforge.web.user.UserSelectPanel;
import org.projectforge.web.wicket.AbstractEditForm;
import org.projectforge.web.wicket.AbstractUnsecureBasePage;
import org.projectforge.web.wicket.WicketUtils;
import org.projectforge.web.wicket.bootstrap.GridBuilder;
import org.projectforge.web.wicket.bootstrap.GridSize;
import org.projectforge.web.wicket.components.*;
import org.projectforge.web.wicket.converter.CurrencyConverter;
import org.projectforge.web.wicket.flowlayout.*;
import org.projectforge.web.wicket.flowlayout.ToggleContainerPanel.ToggleStatus;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class AuftragEditForm extends AbstractEditForm<AuftragDO, AuftragEditPage>
{
  private static final long serialVersionUID = 3150725003240437752L;

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuftragEditForm.class);

  private static final BigDecimal MAX_PERSON_DAYS = new BigDecimal(10000);

  private final PeriodOfPerformanceHelper periodOfPerformanceHelper = new PeriodOfPerformanceHelper();

  private boolean sendEMailNotification = true;

  private RepeatingView positionsRepeater;

  NewCustomerSelectPanel kundeSelectPanel;

  private PaymentSchedulePanel paymentSchedulePanel;

  NewProjektSelectPanel projektSelectPanel;

  private UserSelectPanel projectManagerSelectPanel, headOfBusinessManagerSelectPanel, salesManagerSelectPanel;

  @SpringBean
  private AccessChecker accessChecker;

  @SpringBean
  private RechnungCache rechnungCache;

  @SpringBean
  private AuftragDao auftragDao;

  public AuftragEditForm(final AuftragEditPage parentPage, final AuftragDO data)
  {
    super(parentPage, data);
  }

  public boolean isSendEMailNotification()
  {
    return sendEMailNotification;
  }

  public void setSendEMailNotification(final boolean sendEMailNotification)
  {
    this.sendEMailNotification = sendEMailNotification;
  }

  @Override
  @SuppressWarnings("serial")
  protected void init()
  {
    super.init();

    auftragDao.calculateInvoicedSum(data);

    /* GRID8 - BLOCK */
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Number
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.nummer"));
      final MinMaxNumberField<Integer> number = new MinMaxNumberField<Integer>(InputPanel.WICKET_ID,
          new PropertyModel<Integer>(data,
              "nummer"),
          0, 99999999);
      number.setMaxLength(8).add(AttributeModifier.append("style", "width: 6em !important;"));
      fs.add(number);
      if (NumberHelper.greaterZero(getData().getNummer()) == false) {
        fs.addHelpIcon(getString("fibu.tooltip.nummerWirdAutomatischVergeben"));
      }
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Net sum
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.nettoSumme")).suppressLabelForWarning();
      final DivTextPanel netPanel = new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return CurrencyFormatter.format(data.getNettoSumme());
        }
      }, TextStyle.FORM_TEXT);
      fs.add(netPanel);
      fs.add(new DivTextPanel(fs.newChildId(), ", " + getString("fibu.auftrag.commissioned") + ": "));
      final DivTextPanel orderedPanel = new DivTextPanel(fs.newChildId(), new Model<String>()
      {
        @Override
        public String getObject()
        {
          return CurrencyFormatter.format(data.getBeauftragtNettoSumme());
        }
      }, TextStyle.FORM_TEXT);
      fs.add(orderedPanel);

      String orderInvoiceInfo = I18nHelper.getLocalizedMessage("fibu.auftrag.invoice.info", CurrencyFormatter.format(data.getFakturiertSum()),
          CurrencyFormatter.format(data.getZuFakturierenSum()));
      fs.add(new DivTextPanel(fs.newChildId(), orderInvoiceInfo));
    }
    gridBuilder.newGridPanel();
    {
      // Title
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.titel"));
      final MaxLengthTextField subject = new RequiredMaxLengthTextField(InputPanel.WICKET_ID,
          new PropertyModel<String>(data, "titel"));
      subject.add(WicketUtils.setFocus());
      fs.add(subject);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // reference
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.common.customer.reference"));
      fs.add(new MaxLengthTextField(InputPanel.WICKET_ID, new PropertyModel<String>(data, "referenz")));
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // DropDownChoice status
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("status"));
      final LabelValueChoiceRenderer<AuftragsStatus> statusChoiceRenderer = new LabelValueChoiceRenderer<AuftragsStatus>(
          this,
          AuftragsStatus.values());
      final DropDownChoice<AuftragsStatus> statusChoice = new DropDownChoice<AuftragsStatus>(fs.getDropDownChoiceId(),
          new PropertyModel<AuftragsStatus>(data, "auftragsStatus"), statusChoiceRenderer.getValues(),
          statusChoiceRenderer);
      statusChoice.setNullValid(false).setRequired(true);
      fs.add(statusChoice);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // project
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.projekt")).suppressLabelForWarning();
      projektSelectPanel = new NewProjektSelectPanel(fs.newChildId(), new PropertyModel<>(data, "projekt"), parentPage, "projektId");
      projektSelectPanel.getTextField().add(new AjaxFormComponentUpdatingBehavior("change")
      {
        @Override
        protected void onUpdate(final AjaxRequestTarget target)
        {
          setKundePmHobmAndSmIfEmpty(projektSelectPanel.getModelObject(), target);
        }
      });
      // ajaxUpdateComponents.add(projektSelectPanel.getTextField());
      fs.add(projektSelectPanel);
      projektSelectPanel.init();
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // customer
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.kunde")).suppressLabelForWarning();
      kundeSelectPanel = new NewCustomerSelectPanel(fs.newChildId(), new PropertyModel<KundeDO>(data, "kunde"),
          new PropertyModel<String>(
              data, "kundeText"),
          parentPage, "kundeId");
      kundeSelectPanel.getTextField().setOutputMarkupId(true);
      fs.add(kundeSelectPanel);
      kundeSelectPanel.init();
      fs.addHelpIcon(getString("fibu.auftrag.hint.kannVonProjektKundenAbweichen"));
    }
    gridBuilder.newSplitPanel(GridSize.COL33);
    {
      // project manager
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.projectManager"));
      projectManagerSelectPanel = new UserSelectPanel(fs.newChildId(),
          new PropertyModel<>(data, "projectManager"),
          parentPage, "projectManagerId");
      projectManagerSelectPanel.getFormComponent().setOutputMarkupId(true);
      fs.add(projectManagerSelectPanel);
      projectManagerSelectPanel.init();
    }
    gridBuilder.newSplitPanel(GridSize.COL33);
    {
      // head of business manager
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.headOfBusinessManager"));
      headOfBusinessManagerSelectPanel = new UserSelectPanel(fs.newChildId(),
          new PropertyModel<>(data, "headOfBusinessManager"),
          parentPage, "headOfBusinessManagerId");
      headOfBusinessManagerSelectPanel.getFormComponent().setOutputMarkupId(true);
      fs.add(headOfBusinessManagerSelectPanel);
      headOfBusinessManagerSelectPanel.init();
    }
    gridBuilder.newSplitPanel(GridSize.COL33);
    {
      //sales manager
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.salesManager"));
      salesManagerSelectPanel = new UserSelectPanel(fs.newChildId(),
          new PropertyModel<>(data, "salesManager"),
          parentPage, "salesManagerId");
      salesManagerSelectPanel.getFormComponent().setOutputMarkupId(true);
      fs.add(salesManagerSelectPanel);
      salesManagerSelectPanel.init();
    }
    gridBuilder.newSplitPanel(GridSize.SPAN2);
    {
      // erfassungsDatum
      final FieldProperties<LocalDate> props = getErfassungsDatumProperties();
      final FieldsetPanel fsEntryDate = gridBuilder.newFieldset(getString("fibu.auftrag.erfassung.datum"));
      LocalDatePanel erfassungsDatumPanel = new LocalDatePanel(fsEntryDate.newChildId(), new LocalDateModel(props.getModel()));
      erfassungsDatumPanel.setRequired(true);
      erfassungsDatumPanel.setEnabled(false);
      fsEntryDate.add(erfassungsDatumPanel);
    }
    gridBuilder.newSplitPanel(GridSize.SPAN2);
    {
      // angebotsDatum
      final FieldProperties<LocalDate> props = getAngebotsDatumProperties();
      final FieldsetPanel fsOrderDate = gridBuilder.newFieldset(getString("fibu.auftrag.angebot.datum"));
      LocalDatePanel angebotsDatumPanel = new LocalDatePanel(fsOrderDate.newChildId(), new LocalDateModel(props.getModel()));
      angebotsDatumPanel.setRequired(true);
      fsOrderDate.add(angebotsDatumPanel);
    }
    gridBuilder.newSplitPanel(GridSize.SPAN2);
    {
      // entscheidungsDatum
      final FieldProperties<LocalDate> props = getEntscheidungsDatumProperties();
      final FieldsetPanel fsOrderDate = gridBuilder.newFieldset(getString("fibu.auftrag.entscheidung.datum"));
      LocalDatePanel angebotsDatumPanel = new LocalDatePanel(fsOrderDate.newChildId(), new LocalDateModel(props.getModel()));
      fsOrderDate.add(angebotsDatumPanel);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Bindungsfrist
      final FieldProperties<LocalDate> props = getBindungsfristProperties();
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.bindungsFrist"));
      LocalDatePanel bindungsFristPanel = new LocalDatePanel(fs.newChildId(), new LocalDateModel(props.getModel()));
      fs.add(bindungsFristPanel);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // contact person
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("contactPerson"));
      final UserSelectPanel contactPersonSelectPanel = new UserSelectPanel(fs.newChildId(),
          new PropertyModel<PFUserDO>(data,
              "contactPerson"),
          parentPage, "contactPersonId");
      contactPersonSelectPanel.setRequired(true);
      fs.add(contactPersonSelectPanel);
      contactPersonSelectPanel.init();
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Beauftragungsdatum
      final FieldProperties<LocalDate> props = getBeauftragungsDatumProperties();
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.beauftragungsdatum"));
      LocalDatePanel beauftragungsDatumPanel = new LocalDatePanel(fs.newChildId(), new LocalDateModel(props.getModel()));
      fs.add(beauftragungsDatumPanel);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Period of performance
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.periodOfPerformance"));
      periodOfPerformanceHelper.createPeriodOfPerformanceFields(fs,
          new PropertyModel<>(data, "periodOfPerformanceBegin"),
          new PropertyModel<>(data, "periodOfPerformanceEnd"));
    }
    {
      // Probability of occurrence
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.probabilityOfOccurrence"));
      final MinMaxNumberField<Integer> probabilityOfOccurrence = new MinMaxNumberField<>(InputPanel.WICKET_ID,
          new PropertyModel<>(data, "probabilityOfOccurrence"), 0, 100);
      probabilityOfOccurrence.add(AttributeModifier.append("style", "width: 6em;"));
      fs.add(probabilityOfOccurrence);
    }

    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // Payment schedule
      final ToggleContainerPanel schedulesPanel = new ToggleContainerPanel(gridBuilder.getPanel().newChildId())
      {
        /**
         * @see org.projectforge.web.wicket.flowlayout.ToggleContainerPanel#wantsOnStatusChangedNotification()
         */
        @Override
        protected boolean wantsOnStatusChangedNotification()
        {
          return true;
        }

        /**
         * @see org.projectforge.web.wicket.flowlayout.ToggleContainerPanel#onToggleStatusChanged(AjaxRequestTarget, ToggleStatus)
         *
         */
        @Override
        protected void onToggleStatusChanged(final AjaxRequestTarget target, final ToggleStatus toggleStatus)
        {
          setHeading(getPaymentScheduleHeading(data.getPaymentSchedules(), this));
        }
      };
      schedulesPanel.setHeading(getPaymentScheduleHeading(data.getPaymentSchedules(), schedulesPanel));
      gridBuilder.getPanel().add(schedulesPanel);
      final GridBuilder innerGridBuilder = schedulesPanel.createGridBuilder();
      final DivPanel dp = innerGridBuilder.getPanel();
      dp.add(paymentSchedulePanel = new PaymentSchedulePanel(dp.newChildId(), new CompoundPropertyModel<AuftragDO>(data), getUser()));
      paymentSchedulePanel.setVisible(data.getPaymentSchedules() != null && data.getPaymentSchedules().isEmpty() == false);

      if (getBaseDao().hasLoggedInUserUpdateAccess(data, data, false) == true) {
        final Button addPositionButton = new Button(SingleButtonPanel.WICKET_ID)
        {
          @Override
          public final void onSubmit()
          {
            data.addPaymentSchedule(new PaymentScheduleDO());
            paymentSchedulePanel.rebuildEntries();
            paymentSchedulePanel.setVisible(true);
          }
        };
        final SingleButtonPanel addPositionButtonPanel = new SingleButtonPanel(dp.newChildId(), addPositionButton, getString("add"));
        addPositionButtonPanel.setTooltip(getString("fibu.auftrag.tooltip.addPaymentschedule"));
        dp.add(addPositionButtonPanel);
      }
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // comment
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("comment"));
      fs.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(data, "bemerkung")), true);
    }
    gridBuilder.newSplitPanel(GridSize.COL50);
    {
      // status comment
      final FieldsetPanel fs = gridBuilder.newFieldset(getString("fibu.auftrag.statusBeschreibung"));
      fs.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(data, "statusBeschreibung")),
          true);
    }
    // positions
    gridBuilder.newGridPanel();
    positionsRepeater = gridBuilder.newRepeatingView();
    refreshPositions();
    if (getBaseDao().hasInsertAccess(getUser()) == true) {
      final DivPanel panel = gridBuilder.newGridPanel().getPanel();
      final Button addPositionButton = new Button(SingleButtonPanel.WICKET_ID)
      {
        @Override
        public final void onSubmit()
        {
          getData().addPosition(new AuftragsPositionDO());
          refreshPositions();
          paymentSchedulePanel.rebuildEntries();
        }
      };
      final SingleButtonPanel addPositionButtonPanel = new SingleButtonPanel(panel.newChildId(), addPositionButton,
          getString("add"));
      addPositionButtonPanel.setTooltip(getString("fibu.auftrag.tooltip.addPosition"));
      panel.add(addPositionButtonPanel);
    }
    {
      // email
      gridBuilder.newFieldset(getString("email"))
          .addCheckBox(new PropertyModel<Boolean>(this, "sendEMailNotification"), null)
          .setTooltip(getString("label.sendEMailNotification"));
    }
    add(periodOfPerformanceHelper.createValidator());

    setKundePmHobmAndSmIfEmpty(getData().getProjekt(), null);
  }

  private FieldProperties<LocalDate> getErfassungsDatumProperties() {
    return new FieldProperties<>("fibu.auftrag.angebot.datum", new PropertyModel<>(super.data, "angebotsDatum"));
  }

  private FieldProperties<LocalDate> getAngebotsDatumProperties() {
    return new FieldProperties<>("fibu.auftrag.erfassung.datum", new PropertyModel<>(super.data, "erfassungsDatum"));
  }

  private FieldProperties<LocalDate> getEntscheidungsDatumProperties() {
    return new FieldProperties<>("fibu.auftrag.entscheidung.datum", new PropertyModel<>(super.data, "entscheidungsDatum"));
  }

  private FieldProperties<LocalDate> getBindungsfristProperties() {
    return new FieldProperties<>("fibu.auftrag.bindungsFrist", new PropertyModel<>(super.data, "bindungsFrist"));
  }

  private FieldProperties<LocalDate> getBeauftragungsDatumProperties() {
    return new FieldProperties<>("fibu.auftrag.beauftragungsdatum", new PropertyModel<>(super.data, "beauftragungsDatum"));
  }

  void setKundePmHobmAndSmIfEmpty(final ProjektDO project, final AjaxRequestTarget target)
  {
    if (project == null) {
      return;
    }

    if (getData().getKundeId() == null && StringUtils.isBlank(getData().getKundeText()) == true) {
      getData().setKunde(project.getKunde());
      kundeSelectPanel.getTextField().modelChanged();
      if (target != null) {
        target.add(kundeSelectPanel.getTextField());
      }
    }

    if (getData().getProjectManager() == null) {
      getData().setProjectManager(project.getProjectManager());
      projectManagerSelectPanel.getFormComponent().modelChanged();
      if (target != null) {
        target.add(projectManagerSelectPanel.getFormComponent());
      }
    }

    if (getData().getHeadOfBusinessManager() == null) {
      getData().setHeadOfBusinessManager(project.getHeadOfBusinessManager());
      headOfBusinessManagerSelectPanel.getFormComponent().modelChanged();
      if (target != null) {
        target.add(headOfBusinessManagerSelectPanel.getFormComponent());
      }
    }

    if (getData().getSalesManager() == null) {
      getData().setSalesManager(project.getSalesManager());
      salesManagerSelectPanel.getFormComponent().modelChanged();
      if (target != null) {
        target.add(salesManagerSelectPanel.getFormComponent());
      }
    }
  }

  @SuppressWarnings("serial")
  private void refreshPositions()
  {
    positionsRepeater.removeAll();
    periodOfPerformanceHelper.onRefreshPositions();

    if (CollectionUtils.isEmpty(data.getPositionenIncludingDeleted()) == true) {
      // Ensure that at least one position is available:
      data.addPosition(new AuftragsPositionDO());
    }

    for (final AuftragsPositionDO position : data.getPositionenIncludingDeleted()) {
      final boolean abgeschlossenUndNichtFakturiert = position.isAbgeschlossenUndNichtVollstaendigFakturiert();
      final ToggleContainerPanel positionsPanel = new ToggleContainerPanel(positionsRepeater.newChildId())
      {
        /**
         * @see org.projectforge.web.wicket.flowlayout.ToggleContainerPanel#wantsOnStatusChangedNotification()
         */
        @Override
        protected boolean wantsOnStatusChangedNotification()
        {
          return true;
        }

        /**
         * @see org.projectforge.web.wicket.flowlayout.ToggleContainerPanel#onToggleStatusChanged(AjaxRequestTarget, ToggleStatus)
         */
        @Override
        protected void onToggleStatusChanged(final AjaxRequestTarget target, final ToggleStatus toggleStatus)
        {
          if (toggleStatus == ToggleStatus.OPENED) {
            data.getUiStatus().openPosition(position.getNumber());
          } else {
            data.getUiStatus().closePosition(position.getNumber());
          }
          setHeading(getPositionHeading(position, this));
        }
      };
      if (abgeschlossenUndNichtFakturiert == true) {
        positionsPanel.setHighlightedHeader();
      }
      positionsRepeater.add(positionsPanel);
      if (data.getUiStatus().isClosed(position.getNumber()) == true) {
        positionsPanel.setClosed();
      } else {
        positionsPanel.setOpen();
      }
      positionsPanel.setHeading(getPositionHeading(position, positionsPanel));

      final GridBuilder posGridBuilder = positionsPanel.createGridBuilder();
      posGridBuilder.newGridPanel();
      {
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.auftrag.titel"));
        fs.add(new MaxLengthTextField(InputPanel.WICKET_ID, new PropertyModel<String>(position, "titel")));
      }
      posGridBuilder.newSplitPanel(GridSize.COL33);
      {
        // DropDownChoice type
        final FieldsetPanel fsType = posGridBuilder.newFieldset(getString("fibu.auftrag.position.art"));
        final LabelValueChoiceRenderer<AuftragsPositionsArt> artChoiceRenderer = new LabelValueChoiceRenderer<>(
            fsType,
            AuftragsPositionsArt.values());
        final DropDownChoice<AuftragsPositionsArt> artChoice = new DropDownChoice<>(
            fsType.getDropDownChoiceId(),
            new PropertyModel<AuftragsPositionsArt>(position, "art"), artChoiceRenderer.getValues(), artChoiceRenderer);
        //artChoice.setNullValid(false);
        //artChoice.setRequired(true);
        fsType.add(artChoice);

        // DropDownChoice payment type
        final FieldsetPanel fsPaymentType = posGridBuilder.newFieldset(getString("fibu.auftrag.position.paymenttype"));
        final LabelValueChoiceRenderer<AuftragsPositionsPaymentType> paymentTypeChoiceRenderer = new LabelValueChoiceRenderer<>(
            fsPaymentType,
            AuftragsPositionsPaymentType.values());
        final DropDownChoice<AuftragsPositionsPaymentType> paymentTypeChoice = new DropDownChoice<>(
            fsPaymentType.getDropDownChoiceId(),
            new PropertyModel<AuftragsPositionsPaymentType>(position, "paymentType"), paymentTypeChoiceRenderer.getValues(), paymentTypeChoiceRenderer);
        //paymentTypeChoice.setNullValid(false);
        paymentTypeChoice.setRequired(true);
        fsPaymentType.add(paymentTypeChoice);
      }
      posGridBuilder.newSplitPanel(GridSize.COL33);
      {
        // Person days
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("projectmanagement.personDays"));
        fs.add(new MinMaxNumberField<BigDecimal>(InputPanel.WICKET_ID,
            new PropertyModel<BigDecimal>(position, "personDays"),
            BigDecimal.ZERO, MAX_PERSON_DAYS));
      }
      posGridBuilder.newSplitPanel(GridSize.COL33);
      {
        // Net sum
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.auftrag.nettoSumme"));
        final TextField<String> nettoSumme = new TextField<String>(InputPanel.WICKET_ID, new PropertyModel<>(position, "nettoSumme"))
        {
          @SuppressWarnings({ "rawtypes", "unchecked" })
          @Override
          public IConverter getConverter(final Class type)
          {
            return new CurrencyConverter();
          }
        };
        nettoSumme.setRequired(true);
        fs.add(nettoSumme);
        if (abgeschlossenUndNichtFakturiert == true) {
          fs.setWarningBackground();
        }
      }
      posGridBuilder.newSplitPanel(GridSize.COL25);
      final Set<RechnungsPositionVO> invoicePositionsByOrderPositionId = rechnungCache
          .getRechnungsPositionVOSetByAuftragsPositionId(position.getId());
      final boolean showInvoices = CollectionUtils.isNotEmpty(invoicePositionsByOrderPositionId);
      {
        // Invoices
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.rechnungen")).suppressLabelForWarning();
        if (showInvoices == true) {
          final InvoicePositionsPanel panel = new InvoicePositionsPanel(fs.newChildId());
          fs.add(panel);
          panel.init(invoicePositionsByOrderPositionId);
        } else {
          fs.add(AbstractUnsecureBasePage.createInvisibleDummyComponent(fs.newChildId()));
        }
      }
      posGridBuilder.newSplitPanel(GridSize.COL25);
      {
        // invoiced
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.title.fakturiert")).suppressLabelForWarning();
        if (showInvoices == true) {
          fs.add(new DivTextPanel(fs.newChildId(),
              CurrencyFormatter.format(RechnungDao.getNettoSumme(invoicePositionsByOrderPositionId))));
        } else {
          fs.add(AbstractUnsecureBasePage.createInvisibleDummyComponent(fs.newChildId()));
        }
        if (accessChecker.hasRight(getUser(), RechnungDao.USER_RIGHT_ID, UserRightValue.READWRITE) == true) {
          final DivPanel checkBoxDiv = fs.addNewCheckBoxButtonDiv();
          checkBoxDiv.add(new CheckBoxButton(checkBoxDiv.newChildId(),
              new PropertyModel<Boolean>(position, "vollstaendigFakturiert"),
              getString("fibu.auftrag.vollstaendigFakturiert")));
        }
      }
      posGridBuilder.newSplitPanel(GridSize.COL25);
      {
        // not invoiced
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.title.fakturiert.not")).suppressLabelForWarning();
        if (position.getNettoSumme() != null) {
          BigDecimal invoiced = BigDecimal.ZERO;

          if (showInvoices == true) {
            BigDecimal invoicedSumForPosition = RechnungDao.getNettoSumme(invoicePositionsByOrderPositionId);
            BigDecimal notInvoicedSumForPosition = position.getNettoSumme().subtract(invoicedSumForPosition);
            invoiced = notInvoicedSumForPosition;
          } else {
            invoiced = position.getNettoSumme();
          }
          if (position.getStatus() != null) {
            if (position.getStatus().equals(AuftragsPositionsStatus.ABGELEHNT) || position.getStatus().equals(AuftragsPositionsStatus.ERSETZT) || position
                .getStatus()
                .equals(AuftragsPositionsStatus.OPTIONAL)) {
              invoiced = BigDecimal.ZERO;
            }
          }
          fs.add(new DivTextPanel(fs.newChildId(),
              CurrencyFormatter.format(invoiced)));
        }
      }
      posGridBuilder.newSplitPanel(GridSize.COL25);
      {
        // DropDownChoice status
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("status"));
        final LabelValueChoiceRenderer<AuftragsPositionsStatus> statusChoiceRenderer = new LabelValueChoiceRenderer<AuftragsPositionsStatus>(
            fs, AuftragsPositionsStatus.values());
        final DropDownChoice<AuftragsPositionsStatus> statusChoice = new DropDownChoice<AuftragsPositionsStatus>(
            fs.getDropDownChoiceId(),
            new PropertyModel<AuftragsPositionsStatus>(position, "status"), statusChoiceRenderer.getValues(),
            statusChoiceRenderer);
        statusChoice.setNullValid(true);
        statusChoice.setRequired(true);
        fs.add(statusChoice);
        if (abgeschlossenUndNichtFakturiert == true) {
          fs.setWarningBackground();
        }
      }
      posGridBuilder.newSplitPanel(GridSize.COL100);
      {
        // Task
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("task"));
        final TaskSelectPanel taskSelectPanel = new TaskSelectPanel(fs, new PropertyModel<TaskDO>(position, "task"),
            parentPage, "taskId:"
            + position.getNumber())
        {
          @Override
          protected void selectTask(final TaskDO task)
          {
            super.selectTask(task);
            parentPage.getBaseDao().setTask(position, task.getId());
          }
        };
        fs.add(taskSelectPanel);
        taskSelectPanel.init();
      }

      posGridBuilder.newSplitPanel(GridSize.COL100);
      {
        // Period of performance
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("fibu.periodOfPerformance"));

        final LabelValueChoiceRenderer<ModeOfPaymentType> paymentChoiceRenderer = new LabelValueChoiceRenderer<>(fs, ModeOfPaymentType.values());
        final DropDownChoice<ModeOfPaymentType> paymentChoice = new DropDownChoice<>(fs.getDropDownChoiceId(),
            new PropertyModel<>(position, "modeOfPaymentType"), paymentChoiceRenderer.getValues(), paymentChoiceRenderer);
        paymentChoice.setOutputMarkupPlaceholderTag(true);

        periodOfPerformanceHelper.createPositionsPeriodOfPerformanceFields(fs,
            new PropertyModel<>(position, "periodOfPerformanceType"),
            new PropertyModel<>(position, "periodOfPerformanceBegin"),
            new PropertyModel<>(position, "periodOfPerformanceEnd"),
            paymentChoice);

        fs.add(paymentChoice);
      }

      posGridBuilder.newGridPanel();
      {
        // Comment
        final FieldsetPanel fs = posGridBuilder.newFieldset(getString("comment"));
        fs.add(new MaxLengthTextArea(TextAreaPanel.WICKET_ID, new PropertyModel<String>(position, "bemerkung")));
      }

      if (getBaseDao().hasLoggedInUserUpdateAccess(data, data, false) == true) {
        GridBuilder removeButtonGridBuilder = posGridBuilder.newGridPanel();
        {
          // Remove Position
          DivPanel divPanel = removeButtonGridBuilder.getPanel();
          final Button removePositionButton = new Button(SingleButtonPanel.WICKET_ID)
          {
            @Override
            public final void onSubmit()
            {
              position.setDeleted(true);
              refreshPositions();
              paymentSchedulePanel.rebuildEntries();
            }
          };
          removePositionButton.add(AttributeModifier.append("class", ButtonType.DELETE.getClassAttrValue()));
          final SingleButtonPanel removePositionButtonPanel = new SingleButtonPanel(divPanel.newChildId(), removePositionButton,
              getString("delete"));
          removePositionButtonPanel.setVisible(positionInInvoiceExists(position) == false);
          divPanel.add(removePositionButtonPanel);
        }
      }

      if (position.isDeleted()) {
        positionsPanel.setVisible(false);
      }
    }
  }

  private boolean positionInInvoiceExists(final AuftragsPositionDO position)
  {
    if (position.getId() != null) {
      Set<RechnungsPositionVO> invoicePositionList = rechnungCache.getRechnungsPositionVOSetByAuftragsPositionId(position.getId());
      return invoicePositionList != null && invoicePositionList.isEmpty() == false;
    }
    return false;
  }

  protected String getPositionHeading(final AuftragsPositionDO position, final ToggleContainerPanel positionsPanel)
  {
    if (positionsPanel.getToggleStatus() == ToggleStatus.OPENED) {
      return getString("label.position.short") + " #" + position.getNumber();
    }
    final StringBuffer heading = new StringBuffer();
    heading.append(escapeHtml(getString("label.position.short"))).append(" #").append(position.getNumber());
    heading.append(": ").append(CurrencyFormatter.format(position.getNettoSumme()));
    if (position.getStatus() != null) {
      heading.append(", ").append(getString(position.getStatus().getI18nKey()));
    }
    if (position.getVollstaendigFakturiert() == false) {
      heading.append(" (").append(getString("fibu.fakturiert.not")).append(")");
    }
    if (StringHelper.isNotBlank(position.getTitel()) == true) {
      heading.append(": ").append(StringUtils.abbreviate(position.getTitel(), 80));
    }
    return heading.toString();
  }

  protected String getPaymentScheduleHeading(final List<PaymentScheduleDO> paymentSchedules,
      final ToggleContainerPanel schedulesPanel)
  {
    BigDecimal ges = BigDecimal.ZERO;
    BigDecimal invoiced = BigDecimal.ZERO;
    if (paymentSchedules != null) {
      for (final PaymentScheduleDO schedule : paymentSchedules) {
        if (schedule.getAmount() != null) {
          ges = ges.add(schedule.getAmount());
          if (schedule.getVollstaendigFakturiert() == true) {
            invoiced = invoiced.add(schedule.getAmount());
          }
        }
        if (schedule.getReached() == true && schedule.getVollstaendigFakturiert() == false) {
          schedulesPanel.setHighlightedHeader();
        }
      }
    }
    if (schedulesPanel.getToggleStatus() == ToggleStatus.OPENED) {
      return getString("fibu.auftrag.paymentschedule") + " ("
          + (paymentSchedules != null ? paymentSchedules.size() : "0") + ")";
    }
    final StringBuffer heading = new StringBuffer();
    heading.append(escapeHtml(getString("fibu.auftrag.paymentschedule"))).append(" (")
        .append(paymentSchedules != null ? paymentSchedules.size() : "0").append(")");
    heading.append(": ").append(CurrencyFormatter.format(ges)).append(" ").append(getString("fibu.fakturiert"))
        .append(" ")
        .append(CurrencyFormatter.format(invoiced));
    return heading.toString();
  }

  @Override
  protected Logger getLogger()
  {
    return log;
  }

}
