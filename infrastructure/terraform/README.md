# Infraestructura de Alivia en Azure (Terraform)

Este directorio provisiona la plataforma de la API, PostgreSQL y MongoDB Atlas. **La explicación completa (decisiones,
arquitectura, secretos, región, riesgos) está en [`docs/operations/azure-deployment.md`](../../docs/operations/azure-deployment.md).**
Aquí solo están los comandos.

```
modules/        registry (ACR) | platform (plan + Web App) | postgres (servidor flexible) | mongodb-atlas | deploy-identity (OIDC)
environments/   shared (rg-alivia + ACR) | staging | production
```

## Antes de empezar (una vez)

1. `az login` y `az account set --subscription <id>`; exportar `ARM_SUBSCRIPTION_ID`.
2. Registrar los cuatro proveedores de recursos que se usan (Terraform no los registra solo):
   `az provider register --namespace Microsoft.Web`, `Microsoft.ContainerRegistry`, `Microsoft.DBforPostgreSQL` y
   `Microsoft.ManagedIdentity` (esta última, para la identidad con la que GitHub despliega).
3. Para MongoDB Atlas: `MONGODB_ATLAS_PUBLIC_KEY` y `MONGODB_ATLAS_PRIVATE_KEY` (clave de la organización) en el entorno de
   quien aplica. **Nunca** en un archivo.
4. En cada raíz: `cp terraform.tfvars.example terraform.tfvars` y ajustar (el archivo no se versiona).

## Orden

```bash
# 1. el Resource Group y el ACR
cd environments/shared
terraform init && terraform plan -out tfplan && terraform apply tfplan
terraform output acr_name            # va en acr_name de staging y production

# 2. un entorno, EN DOS PASOS la primera vez
cd ../staging
terraform init
terraform apply -target=module.platform   # crea la Web App y descubre sus IP de salida
terraform plan -out tfplan && terraform apply tfplan
```

**Por qué dos pasos:** PostgreSQL y Atlas solo aceptan las IP de salida de la Web App, y Azure las informa cuando la Web
App ya existe. Un `plan` de todo a la vez falla con *Invalid for_each argument*. Es la única vez que se usa `-target`; en
adelante basta `plan` y `apply`.

## El estado es local

`terraform.tfstate` de cada raíz **no se versiona** (contiene las contraseñas de las bases de datos) y es el único registro
de lo que Terraform creó. Cópielo a un lugar protegido fuera del repositorio después de cada `apply`.

## Verificación sin credenciales

```bash
terraform fmt -check -recursive
terraform init -backend=false && terraform validate
terraform test        # solo en staging y production: proveedores simulados
```

Lo mismo ejecuta el workflow `terraform-validate.yml` en cada pull request que toque este directorio.
