<#macro registrationLayout bodyClass="" displayMessage=true displayInfo=false displayWide=false displayRequiredFields=false>
<#assign currentLang = "en">
<#if locale?? && locale.currentLanguageTag??>
    <#assign currentLang = locale.currentLanguageTag>
</#if>
<!DOCTYPE html>
<html lang="${currentLang}" class="hr-theme-root">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><#nested "title"></title>
    <script>
        (function() {
            try {
                var theme = localStorage.getItem('theme');
                var resolved = theme === 'dark' ? 'dark' : 'light';
                document.documentElement.dataset.theme = resolved;
                document.documentElement.style.colorScheme = resolved;
            } catch (error) {
                document.documentElement.dataset.theme = 'light';
                document.documentElement.style.colorScheme = 'light';
            }
        })();
    </script>
    <link rel="stylesheet" href="${url.resourcesPath}/css/styles.css">
    <script defer src="${url.resourcesPath}/js/theme-toggle.js"></script>
</head>
<body class="hr-login ${bodyClass}">
    <div class="hr-shell">
        <aside class="hr-brand-panel">
            <div class="hr-brand-topbar">
                <#if realm.internationalizationEnabled?? && realm.internationalizationEnabled && locale?? && locale.supported?? && locale.supported?has_content>
                    <nav class="hr-locale-switch" aria-label="${msg('languages')}">
                        <#list locale.supported as l>
                            <a href="${l.url}" class="hr-locale-link <#if locale.currentLanguageTag?? && locale.currentLanguageTag == l.languageTag>is-active</#if>">
                                ${l.label}
                            </a>
                        </#list>
                    </nav>
                </#if>
                <button
                    type="button"
                    class="hr-theme-toggle"
                    data-theme-toggle
                    data-label-light="${msg('themeToggleLight')}"
                    data-label-dark="${msg('themeToggleDark')}"
                    aria-live="polite"
                >
                    <span class="hr-theme-toggle__icon" aria-hidden="true"></span>
                    <span class="hr-theme-toggle__text">${msg("themeToggleDark")}</span>
                </button>
            </div>

            <div class="hr-brand-content">
                <div class="hr-brand-mark">
                    <img src="${url.resourcesPath}/img/logo.png" alt="${msg('brandLogoAlt')}" class="hr-brand-logo">
                    <div class="hr-brand-copy">
                        <p class="hr-brand-kicker">${msg("loginHeroEyebrow")}</p>
                        <h1 class="hr-brand-title">${kcSanitize(msg("loginHeroTitle"))?no_esc}</h1>
                        <p class="hr-brand-text">${msg("loginHeroText")}</p>
                    </div>
                </div>


                <div class="hr-brand-support">
                    <h2 class="hr-brand-support__title">${msg("loginSupportTitle")}</h2>
                    <p class="hr-brand-support__text">${msg("loginSupportText")}</p>
                </div>
            </div>
        </aside>

        <main class="hr-main-panel">
            <section class="hr-auth-card" aria-labelledby="hr-auth-title">
                <div class="hr-auth-header">
                    <div>
                        <p class="hr-auth-kicker">${realm.displayName!'HRIS'}</p>
                        <h2 id="hr-auth-title" class="hr-auth-title"><#nested "header"></h2>
                    </div>
                    <#if displayRequiredFields>
                        <p class="hr-required-note">${msg("requiredFields")}</p>
                    </#if>
                </div>

                <#if displayMessage && message?has_content && (message.type != 'warning' || !(isAppInitiatedAction?? && isAppInitiatedAction))>
                    <div class="hr-alert hr-alert--${message.type}">
                        <span class="hr-alert__icon" aria-hidden="true"></span>
                        <div class="hr-alert__content">
                            <#if kcSanitize??>
                                ${kcSanitize(message.summary)?no_esc}
                            <#else>
                                ${message.summary!''}
                            </#if>
                        </div>
                    </div>
                </#if>

                <div class="hr-auth-form">
                    <#nested "form">
                </div>

                <#if displayInfo>
                    <div class="hr-auth-footer">
                        <#nested "info">
                    </div>
                </#if>

                <#if social?? && social.providers?? && social.providers?size gt 0>
                    <div class="hr-social-block">
                        <#nested "socialProviders">
                    </div>
                </#if>
            </section>

            <p class="hr-footer-note">${msg("loginFooterNote")}</p>
        </main>
    </div>
</body>
</html>
</#macro>
