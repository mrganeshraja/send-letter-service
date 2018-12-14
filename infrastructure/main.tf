provider "azurerm" {
  version = "1.19.0"
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name               = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"

  ftp_private_key        = "${data.azurerm_key_vault_secret.ftp_private_key.value}"
  ftp_public_key         = "${data.azurerm_key_vault_secret.ftp_public_key.value}"
  ftp_user               = "${data.azurerm_key_vault_secret.ftp_user.value}"

  encryption_public_key  = "${data.azurerm_key_vault_secret.encryption_public_key.value}"

  local_env              = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "aat" : "saat" : var.env}"
  local_ase              = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "core-compute-aat" : "core-compute-saat" : local.ase_name}"

  s2s_url                = "http://rpe-service-auth-provider-${local.local_env}.service.${local.local_ase}.internal"
  s2s_vault_url          = "https://s2s-${local.local_env}.vault.azure.net/"

  previewVaultName       = "${var.product}-send-letter"
  nonPreviewVaultName    = "${var.product}-send-letter-${var.env}"
  vaultName              = "${(var.env == "preview" || var.env == "spreview") ? local.previewVaultName : local.nonPreviewVaultName}"

  # URI of vault that stores long-term secrets. It's the app's own Key Vault, except for (s)preview,
  # where vaults are short-lived and can only store secrets generated during deployment
  permanent_vault_uri    = "https://${var.raw_product}-send-letter-${local.local_env}.vault.azure.net/"

  db_connection_options  = "?sslmode=require"

  sku_size = "${var.env == "prod" || var.env == "sprod" || var.env == "aat" ? "I2" : "I1"}"
}

data "azurerm_key_vault_secret" "ftp_user" {
  name      = "ftp-user"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "ftp_private_key" {
  name      = "ftp-private-key"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "ftp_public_key" {
  name      = "ftp-public-key"
  vault_uri = "${local.permanent_vault_uri}"
}

data "azurerm_key_vault_secret" "encryption_public_key" {
  name      = "encryption-public-key"
  vault_uri = "${local.permanent_vault_uri}"
}

module "db" {
  source              = "git@github.com:hmcts/moj-module-postgres?ref=master"
  product             = "${var.product}-${var.component}-db"
  location            = "${var.location_db}"
  env                 = "${var.env}"
  database_name       = "send_letter"
  postgresql_user     = "send_letter"
  sku_name            = "GP_Gen5_2"
  sku_tier            = "GeneralPurpose"
  common_tags         = "${var.common_tags}"
}

module "send-letter-service" {
  source              = "git@github.com:hmcts/moj-module-webapp?ref=master"
  product             = "${var.product}-${var.component}"
  location            = "${var.location_app}"
  env                 = "${var.env}"
  ilbIp               = "${var.ilbIp}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  subscription        = "${var.subscription}"
  capacity            = "${var.capacity}"
  common_tags         = "${var.common_tags}"
  asp_name            = "${var.product}-${var.component}-${var.env}"
  asp_rg              = "${var.product}-${var.component}-${var.env}"
  instance_size       = "${local.sku_size}"

  app_settings = {
    S2S_URL                         = "${local.s2s_url}"
    LETTER_TRACKING_DB_HOST         = "${module.db.host_name}"
    LETTER_TRACKING_DB_PORT         = "${module.db.postgresql_listen_port}"
    LETTER_TRACKING_DB_USER_NAME    = "${module.db.user_name}"
    LETTER_TRACKING_DB_PASSWORD     = "${module.db.postgresql_password}"
    LETTER_TRACKING_DB_NAME         = "${module.db.postgresql_database}"
    LETTER_TRACKING_DB_CONN_OPTIONS = "${local.db_connection_options}"
    FLYWAY_URL                      = "jdbc:postgresql://${module.db.host_name}:${module.db.postgresql_listen_port}/${module.db.postgresql_database}${local.db_connection_options}"
    FLYWAY_USER                     = "${module.db.user_name}"
    FLYWAY_PASSWORD                 = "${module.db.postgresql_password}"
    ENCRYPTION_ENABLED              = "${var.encyption_enabled}"
    SCHEDULING_ENABLED              = "${var.scheduling_enabled}"
    SCHEDULING_LOCK_AT_MOST_FOR     = "${var.scheduling_lock_at_most_for}"
    // ftp
    FTP_HOSTNAME                    = "${var.ftp_hostname}"
    FTP_PORT                        = "${var.ftp_port}"
    FTP_FINGERPRINT                 = "${var.ftp_fingerprint}"
    FTP_TARGET_FOLDER               = "${var.ftp_target_folder}"
    FTP_SMOKE_TEST_TARGET_FOLDER    = "${var.ftp_smoke_test_target_folder}"
    FTP_REPORTS_FOLDER              = "${var.ftp_reports_folder}"
    FTP_REPORTS_CRON                = "${var.ftp_reports_cron}"
    FTP_USER                        = "${local.ftp_user}"
    FTP_PRIVATE_KEY                 = "${local.ftp_private_key}"
    FTP_PUBLIC_KEY                  = "${local.ftp_public_key}"
    ENCRYPTION_PUBLIC_KEY           = "${local.encryption_public_key}"
  }
}

# region save DB details to Azure Key Vault
module "send-letter-key-vault" {
  source              = "git@github.com:hmcts/moj-module-key-vault?ref=master"
  name                = "${local.vaultName}"
  product             = "${var.product}"
  env                 = "${var.env}"
  tenant_id           = "${var.tenant_id}"
  object_id           = "${var.jenkins_AAD_objectId}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  # dcd_cc-dev group object ID
  product_group_object_id = "38f9dea6-e861-4a50-9e73-21e64f563537"
}

resource "azurerm_key_vault_secret" "POSTGRES-USER" {
  name      = "${var.component}-POSTGRES-USER"
  value     = "${module.db.user_name}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "POSTGRES-PASS" {
  name      = "${var.component}-POSTGRES-PASS"
  value     = "${module.db.postgresql_password}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "POSTGRES_HOST" {
  name      = "${var.component}-POSTGRES-HOST"
  value     = "${module.db.host_name}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "POSTGRES_PORT" {
  name      = "${var.component}-POSTGRES-PORT"
  value     = "${module.db.postgresql_listen_port}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "POSTGRES_DATABASE" {
  name      = "${var.component}-POSTGRES-DATABASE"
  value     = "${module.db.postgresql_database}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}
# endregion
