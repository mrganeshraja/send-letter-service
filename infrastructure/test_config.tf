
resource "azurerm_key_vault_secret" "test-s2s-url" {
  name      = "test-s2s-url"
  value     = "${local.s2s_url}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-s2s-name" {
  name      = "test-s2s-name"
  value     = "send_letter_tests"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-s2s-secret" {
  name      = "test-s2s-secret"
  value     = "${data.vault_generic_secret.tests_s2s_secret.data["value"]}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-hostname" {
  name      = "test-ftp-hostname"
  value     = "${var.ftp_hostname}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-port" {
  name      = "test-ftp-port"
  value     = "${var.ftp_port}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-fingerprint" {
  name      = "test-ftp-fingerprint"
  value     = "${var.ftp_fingerprint}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-target-folder" {
  name      = "test-ftp-target-folder"
  value     = "${var.ftp_smoke_test_target_folder}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-user" {
  name      = "test-ftp-user"
  value     = "${local.ftp_user}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-private-key" {
  name      = "test-ftp-private-key"
  value     = "${local.ftp_private_key}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-ftp-public-key" {
  name      = "test-ftp-public-key"
  value     = "${local.ftp_public_key}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}

resource "azurerm_key_vault_secret" "test-encryption-enabled" {
  name      = "test-encryption-enabled"
  value     = "${var.encyption_enabled}"
  vault_uri = "${module.send-letter-key-vault.key_vault_uri}"
}
