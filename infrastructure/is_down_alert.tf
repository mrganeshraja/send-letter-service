//module "send-letter-up-alert" {
//  source            = "git@github.com:hmcts/cnp-module-metric-alert"
//  location          = "${azurerm_application_insights.appinsights.location}"
//  app_insights_name = "${azurerm_application_insights.appinsights.name}"
//
//  alert_name = "Send Letter is DOWN - RPE"
//  alert_desc = "Triggers when send letter service looks like being down within a 15 minutes timeframe."
//
//  app_insights_query = <<EOF
//union
//  (dependencies
//    | where data == 'FtpFileUploaded' and success =~ 'true'
//    | summarize ['name'] = 'uploaded', ['counter'] = sum(itemCount)
//  ),
//  (requests
//    | where name == 'POST SendLetterController/sendLetter' and success =~ 'true'
//    | where tostring(operation_SyntheticSource) == ''
//    | summarize ['name'] = 'received', ['counter'] = sum(itemCount)
//  ),
//  (traces
//    | extend logger = customDimensions.LoggerName
//    | where logger == 'uk.gov.hmcts.reform.sendletter.tasks.UploadLettersTask' and message has 'FTP downtime'
//    | summarize ['name'] = 'downtime', ['counter'] = sum(itemCount)
//  )
//| evaluate pivot(name, sum(counter))
//| project ['is_operational'] = iif(received > 0 and downtime > 0, true, iif(received > 0, uploaded > 0, true))
//| where is_operational == false
//EOF
//
//  frequency_in_minutes       = 10
//  time_window_in_minutes     = 15
//  severity_level             = "2"
//  action_group_name          = "${module.is-down-action-group.action_group_name}"
//  custom_email_subject       = "Send Letter is DOWN"
//  trigger_threshold_operator = "GreaterThan"
//  trigger_threshold          = 0
//  resourcegroup_name         = "${azurerm_resource_group.rg.name}"
//}
