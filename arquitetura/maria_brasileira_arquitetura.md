# Documento de Arquitetura do Sistema Maria Brasileira

## ADR-01: Adoção do Estilo Arquitetural N-Camadas

**Contexto:**
O Sistema Maria Brasileira é uma aplicação web com múltiplos módulos funcionais (clientes, prestadores, agendamentos, serviços, vendas, receitas e despesas) e três perfis de usuário distintos (Administrador, Cliente e Prestador de Serviço). Há necessidade de organização clara de responsabilidades, facilidade de manutenção dos módulos e suporte a futuras evoluções sem impacto simultâneo em todas as partes do sistema.

**Decisão:**
Adotar o estilo arquitetural N-Camadas, separando o sistema nas camadas de Apresentação, Negócio e Dados, com comunicação sequencial entre elas via requisições HTTP (Apresentação → Negócio) e consultas SQL (Negócio → Dados).

**Status:** Aceito

**Consequências:**
- Separação clara de responsabilidades entre as camadas, facilitando a manutenção e a evolução independente de cada módulo funcional do sistema.
- Aumento do número de artefatos e maior complexidade inicial de configuração do ambiente de desenvolvimento e implantação em comparação a uma arquitetura monolítica simples.

**Requisitos relacionados:** RF001, RF005, RF009, RF017, RF021, RF025, NF001, NF002, NF005.

---

## ADR-02: Verificação de Disponibilidade de Prestadores no Agendamento

**Contexto:**
O requisito RF009 determina que, ao cadastrar um agendamento, o sistema deve verificar se o prestador de serviço de preferência do cliente está disponível na data e horário escolhidos. Sem essa verificação na camada de negócio, agendamentos conflitantes poderiam ser criados, comprometendo a operação da franquia e a experiência do cliente.

**Decisão:**
Implementar na camada de negócio, dentro do módulo de Agendamentos, uma lógica dedicada de verificação de disponibilidade de prestadores. Antes de confirmar um novo agendamento, o sistema deve consultar os agendamentos já registrados no banco de dados e verificar se há conflito de data, horário e prestador.

**Status:** Aceito

**Consequências:**
- Garante a integridade dos agendamentos cadastrados, evitando conflitos de agenda e aumentando a confiabilidade do sistema para clientes e administradores.
- Aumenta a complexidade da camada de negócio e pode impactar o desempenho em cenários de alto volume de agendamentos simultâneos, exigindo atenção à eficiência das consultas ao banco de dados.

**Requisitos relacionados:** RF009, NF005.

---

## ADR-03: Controle de Acesso Baseado em Perfis de Usuário

**Contexto:**
O sistema possui três perfis de usuário com responsabilidades distintas: o Administrador tem acesso completo ao sistema e gerencia prestadores de serviço, vendas, receitas e despesas; o Cliente realiza e acompanha agendamentos e gerencia seus próprios dados cadastrais; e o Prestador de Serviço é cadastrado pelo administrador após aprovação em processo seletivo e tem seus dados e disponibilidade gerenciados no sistema. Operações sensíveis, como o gerenciamento financeiro e o cadastro de prestadores, não devem estar acessíveis ao perfil Cliente.

**Decisão:**
Implementar controle de acesso baseado em perfis, Role-Based Access Control (RBAC), na camada de negócio, restringindo as funcionalidades disponíveis conforme o tipo de usuário autenticado: o perfil Administrador terá acesso completo a todos os módulos; o perfil Cliente terá acesso apenas aos módulos de agendamento e gerenciamento do próprio cadastro; e o Prestador de Serviço terá seus dados gerenciados exclusivamente pelo Administrador.

**Status:** Aceito

**Consequências:**
- Aumenta a segurança do sistema, impedindo que clientes acessem informações financeiras ou realizem operações administrativas, como o cadastro e exclusão de prestadores de serviço.
- Requer manutenção contínua das regras de permissão à medida que novas funcionalidades forem adicionadas ao sistema, aumentando o esforço de gestão do controle de acesso.

**Requisitos relacionados:** RF001, RF005, RF017, RF021, RF025.

---

## ADR-04: Módulo Financeiro Integrado com Agendamentos e Vendas

**Contexto:**
Os requisitos RF017, RF021 e RF025 definem um módulo de gestão financeira que registra vendas, receitas e despesas da empresa. O requisito RF021 determina explicitamente que a receita só pode ser registrada após o serviço agendado ter sido finalizado e o pagamento realizado, exigindo integração direta com os módulos de agendamento (RF009) e de vendas (RF017).

**Decisão:**
Implementar o módulo financeiro na camada de negócio com entidades próprias para Receita e Despesa, integradas às entidades de Venda e Agendamento. O cadastro de receita deve verificar a existência de uma venda associada a um agendamento concluído antes de permitir o registro.

**Status:** Aceito

**Consequências:**
- Permite ao administrador ter uma visão consolidada da saúde financeira da franquia diretamente no sistema, com rastreabilidade entre agendamentos, vendas e receitas.
- Aumenta o acoplamento entre os módulos financeiro, de vendas e de agendamentos, exigindo cuidado especial ao realizar alterações em qualquer um desses módulos para evitar inconsistências nos dados financeiros.

**Requisitos relacionados:** RF017, RF021, RF025.

---

## ADR-05: Validação de Dados em Duas Etapas

**Contexto:**
Diversos requisitos funcionais, como RF001, RF005 e RF009, exigem validação de campos obrigatórios e tratamento de exceções como CPF e e-mail duplicados, campos em branco, senha com menos de oito caracteres e senha informada incorretamente. A validação centralizada apenas no servidor aumentaria o número de requisições desnecessárias e prejudicaria a experiência do usuário, impactando negativamente o requisito NF005 (agilidade).

**Decisão:**
Adotar validação em duas etapas: validação básica de formato e preenchimento de campos obrigatórios na camada de apresentação (front-end), fornecendo feedback imediato ao usuário; e validação das regras de negócio (duplicidade por CPF/e-mail, integridade referencial, consistência de dados) na camada de negócio (back-end).

**Status:** Aceito

**Consequências:**
- Melhora a experiência do usuário com feedback imediato sobre erros de preenchimento, reduz o número de requisições desnecessárias ao servidor e contribui para o atendimento do requisito NF005.
- Gera duplicação parcial de lógica de validação entre as camadas de apresentação e negócio, exigindo sincronização entre elas sempre que regras de validação forem modificadas.

**Requisitos relacionados:** RF001, RF005, RF009, NF005.

---

## ADR-06: Adoção de Banco de Dados Relacional

**Contexto:**
O sistema envolve entidades fortemente relacionadas entre si: um agendamento (RF009) depende de um cliente (RF001) e de um prestador de serviço (RF005); uma venda (RF017) depende de um agendamento; e uma receita (RF021) só pode ser registrada após a conclusão de uma venda. Além disso, o requisito RF004 determina que um cliente não pode ser excluído enquanto tiver agendamentos pendentes, o que exige verificação de integridade referencial antes de qualquer operação de remoção.

**Decisão:**
Adotar o MySQL como banco de dados relacional na camada de dados, utilizando chaves estrangeiras e restrições de integridade para garantir a consistência entre as entidades do sistema. As operações que envolvem múltiplas entidades, como o registro de uma receita vinculada a uma venda e a um agendamento, devem ser tratadas como transações atômicas.

**Status:** Aceito

**Consequências:**
- Garante a integridade referencial entre as entidades do sistema, impedindo a exclusão de clientes com agendamentos pendentes (RF004) ou o registro de receitas sem venda associada (RF021), o que aumenta a confiabilidade dos dados financeiros e operacionais.
- O uso de transações e restrições relacionais pode aumentar a complexidade das consultas e reduzir a flexibilidade do modelo de dados em eventuais mudanças de escopo, exigindo maior cuidado no planejamento do esquema desde o início do desenvolvimento.

**Requisitos relacionados:** RF017, RF021, RF025.
